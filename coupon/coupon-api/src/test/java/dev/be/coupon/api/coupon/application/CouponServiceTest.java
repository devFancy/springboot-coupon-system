package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.exception.CouponIssueException;
import dev.be.coupon.api.coupon.application.exception.InvalidIssuedCouponException;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.api.coupon.infrastructure.kafka.dto.CouponIssueMessage;
import dev.be.coupon.api.coupon.infrastructure.kafka.producer.CouponIssueProducer;
import dev.be.coupon.api.coupon.infrastructure.redis.AppliedUserRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCacheRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCountRedisRepository;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.domain.coupon.exception.CouponAlreadyUsedException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponTypeException;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CouponServiceTest 주의사항:
 * <p>
 * 이 테스트는 Kafka Consumer 가 실제 DB(JPA Repository)에 접근해 데이터를 저장하는 구조이므로,
 * 테스트에서도 Spring Context 에서 관리하는 실제 JPA 기반 Repository 를 사용해야 합니다.
 * <p>
 * 특히, InMemoryCouponRepository 또는 InMemoryIssuedCouponRepository 를 직접 생성해 사용할 경우,
 * Kafka Consumer 가 접근하는 Repository 와 분리되어 테스트 결과가 어긋나게 됩니다.
 * <p>
 * 따라서 아래 Repository 들은 모두 @Autowired 로 주입 받아야 합니다:
 * - CouponRepository
 * - IssuedCouponRepository
 * 해당 테스트를 하기 위한 사전 준비 사항
 * - Docker Compose 실행 - MySQL, Redis, Kafka 구동되어야 함
 * - Kafka Application 실행
 */
@SpringBootTest
class CouponServiceTest {

    private CouponService couponService;
    private FakeUserRoleChecker userRoleChecker;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponCacheRepository couponCacheRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AppliedUserRepository appliedUserRepository;

    @BeforeEach
    void setUp() {
        userRoleChecker = new FakeUserRoleChecker();
        userRoleChecker.updateIsAdmin(true); // 기본값으로 관리자 권한을 부여함

        CouponCountRedisRepository couponCountRedisRepository = new CouponCountRedisRepository(redisTemplate);
        CouponIssueProducer couponIssueProducer = new CouponIssueProducer(kafkaTemplate);

        couponService = new CouponService(
                couponRepository,
                couponCacheRepository,
                couponCountRedisRepository,
                couponIssueProducer,
                issuedCouponRepository,
                appliedUserRepository,
                userRoleChecker
        );
    }

    @AfterEach
    void tearDown() {
        safeDelete("coupon_count:*");
        safeDelete("lock:coupon:*");
        safeDelete("applied_user:*");
        safeDelete("coupon:*");
    }

    private void safeDelete(final String pattern) {
        Optional.ofNullable(redisTemplate.keys(pattern))
                .ifPresent(keys -> keys.stream()
                        .filter(Objects::nonNull)
                        .forEach(redisTemplate::delete));
    }

    @DisplayName("관리자가 쿠폰을 생성한다.")
    @Test
    void success_coupon() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when
        final CouponCreateResult actual = couponService.create(expected);

        // then
        assertThat(actual.id()).isNotNull();
        assertThat(actual.name()).isEqualTo(expected.name());
    }

    @DisplayName("쿠폰을 생성할 때 기존 유형에 없으면 예외가 발생한다.")
    @ParameterizedTest(name = "쿠폰 유형: {0}")
    @ValueSource(strings = {"KHICKEN", "BIZZA", "VURGER"})
    void should_throw_exception_when_coupon_type_is_invalid(final String type) {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", type, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when & then
        assertThatThrownBy(() -> couponService.create(expected)).isInstanceOf(InvalidCouponTypeException.class).hasMessage("쿠폰 타입이 지정되지 않았습니다.");
    }

    @DisplayName("쿠폰을 생성할 때 관리자 권한이 아니라면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_user_is_not_admin() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        userRoleChecker.updateIsAdmin(false); // 관리자 권한이 아닌 경우: false

        // when & then
        assertThatThrownBy(() -> couponService.create(expected)).isInstanceOf(UnauthorizedAccessException.class).hasMessage("권한이 없습니다.");
    }

    @DisplayName("사용자가 쿠폰 발급 요청을 하면 쿠폰 발급이 처리된다.")
    @Test
    void success_issued_coupon() {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand createCommand = new CouponCreateCommand(userId, "피자 할인 쿠폰", "PIZZA", 10, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult created = couponService.create(createCommand);
        final UUID couponId = created.id();

        final CouponIssueCommand issueCommand = new CouponIssueCommand(userId, couponId);

        // when
        final CouponIssueResult result = couponService.issue(issueCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isFalse();
        assertThat(result.issuedAt()).isNotNull();
    }

    @DisplayName("사용자: 동일 쿠폰에 대해 여러 번 '쿠폰 받기'를 요청해도, 1회만 발급받는다. (단일 스레드)")
    @Test
    void should_only_issue_once_for_same_user() {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(userId, "햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(command);
        final UUID couponId = result.id();

        // when
        for (int i = 0; i < 1000; i++) {
            try {
                couponService.issue(new CouponIssueCommand(userId, couponId));
            } catch (Exception e) {
                // 개별 발급 시도 중 발생하는 예외는 여기서는 무시합니다. (실제 서비스 로직 및 예외 타입에 따라 조정 필요)
            }
        }

        // then
        final String appliedUserKey = "applied_user:" + couponId;

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Redis 중복 발급 방지 키 확인 (사용자 ID가 하나만 있어야 함)
                    final Long appliedUserSetSize = redisTemplate.opsForSet().size(appliedUserKey);
                    assertThat(appliedUserSetSize)
                            .as("Redis 'applied_user:%s' 세트에는 해당 쿠폰에 대해 사용자 ID가 하나만 존재해야 합니다.", couponId)
                            .isEqualTo(1L);

                    final Optional<IssuedCoupon> issuedCouponOptional = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId);
                    assertThat(issuedCouponOptional)
                            .as("쿠폰 ID %s와 사용자 ID %s에 해당하는 발급된 쿠폰이 존재해야 합니다.", couponId, userId)
                            .isPresent();

                    // 데이터베이스에 최종적으로 발급된 쿠폰 수 확인
                    assertThat(issuedCouponRepository.countByCouponId(couponId))
                            .as("DB에는 쿠폰 ID %s와 사용자 ID %s에 대해 쿠폰이 하나만 발급되어야 합니다.", couponId, userId)
                            .isEqualTo(1L);
                });
        System.out.println("최종 쿠폰 발급 수 (특정 사용자 DB 기준): " + issuedCouponRepository.countByCouponId(couponId));
        System.out.println("Redis 'applied_user' 세트 내 사용자 수: " + redisTemplate.opsForSet().size(appliedUserKey));
        System.out.println("남은 쿠폰 수량: " + (result.totalQuantity() - issuedCouponRepository.countByCouponId(couponId)) );
    }

    @DisplayName("사용자: 동일 쿠폰에 대해 동시에 여러 번 '쿠폰 받기'를 요청해도, 1회만 발급받는다. (멀티 스레드)")
    @Test
    void should_only_issue_once_for_same_user_multi_thread() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(userId, "햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(command);
        final UUID couponId = result.id();

        int numberOfThreads = 100;
        int numberOfRequests = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);

        // when
        for (int i = 0; i < numberOfRequests; i++) {
            executor.execute(() -> {
                try {
                    couponService.issue(new CouponIssueCommand(userId, couponId));
                } catch (Exception e) {
                    // 개별 발급 시도 중 발생하는 예외는 여기서는 무시합니다. (실제 서비스 로직 및 예외 타입에 따라 조정 필요)
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            System.err.println("스레드 풀이 시간 내에 완전히 종료되지 않았습니다.");
            executor.shutdownNow();
        }

        // then
        final String appliedUserKey = "applied_user:" + couponId;
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    final Long appliedUserSetSize = redisTemplate.opsForSet().size(appliedUserKey);
                    assertThat(appliedUserSetSize)
                            .as("Redis 'applied_user:%s' 세트에는 해당 쿠폰에 대해 사용자 ID가 하나만 존재해야 합니다.", couponId)
                            .isEqualTo(1L);

                    final Optional<IssuedCoupon> issuedCouponOptional = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId);
                    assertThat(issuedCouponOptional)
                            .as("쿠폰 ID %s와 사용자 ID %s에 해당하는 발급된 쿠폰이 존재해야 합니다.", couponId, userId)
                            .isPresent();

                    assertThat(issuedCouponRepository.countByCouponId(couponId))
                            .as("DB에는 쿠폰 ID %s와 사용자 ID %s에 대해 쿠폰이 하나만 발급되어야 합니다.", couponId, userId)
                            .isEqualTo(1L);
                });

        System.out.println("최종 쿠폰 발급 수 (특정 사용자 DB 기준): " + issuedCouponRepository.countByCouponId(couponId));
        System.out.println("Redis 'applied_user' 세트 내 사용자 수: " + redisTemplate.opsForSet().size(appliedUserKey));
    }

    @DisplayName("선착순 쿠폰: 500개 한정 쿠폰에 1,000명이 '쿠폰 받기' 요청 시, 500명에게만 발급한다. (단일 스레드)")
    @Test
    void should_only_issue_up_to_total_quantity_limit() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(adminId, "피자 쿠폰", "PIZZA", 500, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final CouponCreateResult result = couponService.create(command);
        final UUID couponId = result.id();

        // when
        for (int i = 0; i < 1000; i++) {
            UUID userId = UUID.randomUUID();
            try {
                couponService.issue(new CouponIssueCommand(userId, couponId));
            } catch (Exception ignore) {
                // 개별 발급 시도 중 발생하는 예외는 여기서는 무시합니다. (실제 서비스 로직 및 예외 타입에 따라 조정 필요)
            }
        }

        // then
        final String redisAppliedUserKey = "applied_user:" + couponId;
        final String redisCouponCountKey = "coupon_count:" + couponId;

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    final Long appliedUserSetSize = redisTemplate.opsForSet().size(redisAppliedUserKey);
                    assertThat(appliedUserSetSize)
                            .as("Redis 'applied_user:%s' set 쿠폰 발급에 성공한 사용자 수(500명)가 있어야 합니다.", couponId)
                            .isEqualTo(500L);
                    assertThat(issuedCouponRepository.countByCouponId(couponId))
                            .as("DB에 쿠폰 ID(%s) 가 최종적으로 총 500개 발급되어야 합니다.", couponId)
                            .isEqualTo(500L);
                    final String currentRedisCount = redisTemplate.opsForValue().get(redisCouponCountKey);
                    assertThat(currentRedisCount)
                            .as("Redis 'coupon_count:%s' 값은 발급된 총 쿠폰 수(500)여야 합니다", couponId)
                            .isEqualTo("500");
                });

        System.out.println("최종 쿠폰 발급 수 (DB): " + issuedCouponRepository.countByCouponId(couponId));
        System.out.println("Redis 'applied_user' 세트 크기: " + redisTemplate.opsForSet().size(redisAppliedUserKey));
        System.out.println("Redis 'coupon_count' 값: " + redisTemplate.opsForValue().get(redisCouponCountKey));
    }

    @DisplayName("선착순 쿠폰(동시 요청): 500개 한정 쿠폰에 여러명이 동시에 '쿠폰 받기' 요청해도, 500개만 발급한다. (멀티 스레드)")
    @Test
    void should_only_issue_up_to_total_quantity_limit_multithreaded() throws InterruptedException {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(adminId, "피자 쿠폰", "PIZZA", 500, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final UUID couponId = couponService.create(command).id();

        int numberOfThreads = 100;
        int numberOfRequests = 10000;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);

        // when
        for (int i = 0; i < numberOfRequests; i++) {
            final UUID userId = UUID.randomUUID(); // 각각 다른 사용자
            executor.execute(() -> {
                try {
                    couponService.issue(new CouponIssueCommand(userId, couponId));
                } catch (InvalidIssuedCouponException ignore) {
                    // 수량 초과 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            System.err.println("스레드 풀이 시간 내에 완전히 종료되지 않았습니다.");
            executor.shutdownNow();
        }


        // then
        final String redisCouponCountKey = "coupon_count:" + couponId;
        final String redisAppliedUserKey = "applied_user:" + couponId;

        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    final String currentRedisCount = redisTemplate.opsForValue().get(redisCouponCountKey);
                    assertThat(currentRedisCount)
                            .as("Redis 'coupon_count:%s' 값은 발급된 총 쿠폰 수(500)여야 합니다.", couponId)
                            .isEqualTo("500");

                    assertThat(issuedCouponRepository.countByCouponId(couponId))
                            .as("DB에는 쿠폰 ID(%s)에 대해 최종적으로 발급된 쿠폰 수(500개)가 반영되어야 합니다.", couponId)
                            .isEqualTo(500L);

                    final Long appliedUserSetSize = redisTemplate.opsForSet().size(redisAppliedUserKey);
                    assertThat(appliedUserSetSize)
                            .as("Redis 'applied_user:%s' 세트에는 쿠폰 발급에 성공한 사용자 수(500명)가 있어야 합니다.", couponId)
                            .isEqualTo(500L);
                });

        System.out.println("최종 쿠폰 발급 수 (DB): " + issuedCouponRepository.countByCouponId(couponId));
        System.out.println("Redis 'applied_user' 세트 크기: " + redisTemplate.opsForSet().size(redisAppliedUserKey));
        System.out.println("Redis 'coupon_count' 값: " + redisTemplate.opsForValue().get(redisCouponCountKey));
    }

    @DisplayName("쿠폰 소진 후 추가 '쿠폰 받기' 요청 시, 전체 발급 수량은 기존 소진 수량으로 정확히 유지된다. (롤백 확인)")
    @Test
    void should_maintain_correct_count_when_quantity_exceeded() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(adminId, "수량 1 쿠폰", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final UUID couponId = couponService.create(command).id();

        final UUID user1 = UUID.randomUUID();
        final UUID user2 = UUID.randomUUID();

        // when
        couponService.issue(new CouponIssueCommand(user1, couponId));
        try {
            couponService.issue(new CouponIssueCommand(user2, couponId));
        } catch (Exception ignored) {
            // 개별 발급 시도 중 발생하는 예외는 여기서는 무시합니다. (실제 서비스 로직 및 예외 타입에 따라 조정 필요)
        }

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(issuedCouponRepository.findByUserIdAndCouponId(user1, couponId))
                            .as("user1의 쿠폰은 DB에 존재해야 합니다.")
                            .isPresent();
                    assertThat(issuedCouponRepository.findByUserIdAndCouponId(user2, couponId))
                            .as("user2의 쿠폰은 DB에 존재해야 합니다.")
                            .isNotPresent();
                });

        // then
        final String countKey = "coupon_count:" + couponId;
        final Long count = Long.parseLong(Objects.requireNonNull(redisTemplate.opsForValue().get(countKey)));
        assertThat(count).isEqualTo(1); // 롤백이 되지 않으면 2가 됨
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
    }

    @DisplayName("[쿠폰 발급 후처리] 사용자가 쿠폰 발급 성공 시, 최종 등록을 위해 Kafka 에 발급 정보를 전달한다.")
    @Test
    void should_send_kafka_message_on_issue() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand createCommand = new CouponCreateCommand(
                adminId, "치킨", "CHICKEN", 10, LocalDateTime.now(), LocalDateTime.now().plusDays(1)
        );
        final UUID couponId = couponService.create(createCommand).id();

        final CouponIssueCommand issueCommand = new CouponIssueCommand(adminId, couponId);

        // when
        final CouponIssueResult result = couponService.issue(issueCommand);

        // then
        assertThat(result.userId()).isEqualTo(adminId);
        assertThat(result.couponId()).isEqualTo(couponId);
        System.out.println("Kafka 메시지 전송 완료 (터미널에서 확인)");
    }

    @DisplayName("[쿠폰 대량 발급 후처리]: 1000명의 사용자에게 쿠폰 발급 시, 각 사용자의 발급 정보가 Kafka 로 전달된다.")
    @Test
    void should_send_kafka_message_for_1000_users() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand createCommand = new CouponCreateCommand(
                adminId, "1000명 대상 테스트 쿠폰", "CHICKEN", 100, LocalDateTime.now(), LocalDateTime.now().plusDays(1)
        );
        final UUID couponId = couponService.create(createCommand).id();

        // when
        int successfulIssues = 0;      // 실제 쿠폰 발급에 성공한 횟수
        int businessRuleFailures = 0;  // 수량 초과, 중복 발급 등 비즈니스 규칙에 의해 실패한 횟수
        int exceptionFailures = 0;     // CouponIssueException 등 실제 예외가 발생한 횟수

        for (int i = 0; i < 1000; i++) {
            final UUID userId = UUID.randomUUID();
            try {
                final CouponIssueResult result = couponService.issue(new CouponIssueCommand(userId, couponId));
                if (!result.alreadyIssued() && !result.quantityExceeded()) {
                    assertThat(result.userId()).isEqualTo(userId);
                    assertThat(result.couponId()).isEqualTo(couponId);
                    successfulIssues++;
                } else if (result.quantityExceeded()) {
                    businessRuleFailures++;
                } else {
                    businessRuleFailures++;
                }
            } catch (CouponIssueException e) {
                System.err.println("CouponIssueException 발생: " + e.getMessage());
                exceptionFailures++;
            } catch (Exception e) {
                System.err.println("예상치 못한 예외 발생: " + e.getMessage());
                e.printStackTrace();
                exceptionFailures++;
            }
        }

        // then
        System.out.println("실제 발급 성공 횟수: " + successfulIssues);
        System.out.println("비즈니스 규칙 실패 횟수 (수량 초과 등): " + businessRuleFailures);
        System.out.println("예외 발생 횟수: " + exceptionFailures);

        String redisKey = "coupon_count:" + couponId;
        String countValue = redisTemplate.opsForValue().get(redisKey);
        assertThat(countValue).isEqualTo("100");

        assertThat(successfulIssues).as("실제 성공적으로 발급된 쿠폰 수").isEqualTo(100);
        assertThat(businessRuleFailures).as("수량 초과 등으로 발급되지 않은 쿠폰 수").isEqualTo(900);
        assertThat(exceptionFailures).as("발급 중 발생한 예외 수").isZero();
    }

    @DisplayName("쿠폰을 사용하면 사용 처리된다.")
    @Test
    void success_coupon_usage() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final UUID couponId = createAndIssueCoupon(
                adminId, userId, 10, now.minusDays(1), now.plusDays(1)
        );

        // when
        final CouponUsageResult result = couponService.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isTrue();
        assertThat(result.usedAt()).isNotNull();
    }

    @DisplayName("발급받지 않은 쿠폰을 사용하려고 하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_not_issued() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID otherUserId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        // when
        final UUID couponId = createAndIssueCoupon(
                adminId, userId, 10, now.minusDays(1), now.plusDays(1)
        );

        // then
        assertThatThrownBy(() -> couponService.usage(new CouponUsageCommand(otherUserId, couponId)))
                .isInstanceOf(IssuedCouponNotFoundException.class)
                .hasMessage("발급되지 않았거나 소유하지 않은 쿠폰입니다.");

    }

    @DisplayName("이미 사용한 쿠폰을 다시 사용하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_already_used() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final UUID couponId = createAndIssueCoupon(
                adminId, userId, 1, now.minusDays(1), now.plusDays(1)
        );

        // when
        couponService.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThatThrownBy(() -> couponService.usage(new CouponUsageCommand(userId, couponId)))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");

    }

    private UUID createAndIssueCoupon(final UUID adminId, final UUID userId,
                                      final int quantity,
                                      final LocalDateTime validFrom, final LocalDateTime validUntil) {
        // 1. 쿠폰 생성
        final UUID couponId = couponService.create(new CouponCreateCommand(
                adminId, "치킨 쿠폰", "CHICKEN", quantity, validFrom, validUntil
        )).id();

        // 2. 쿠폰 발급
        final CouponIssueResult issued = couponService.issue(new CouponIssueCommand(userId, couponId));
        assertThat(issued.used()).isFalse();

        // 3. Kafka Consumer 가 비동기로 쿠폰 발급을 저장하는 동안 대기
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(issuedCouponRepository.findByUserIdAndCouponId(userId, couponId))
                        .isPresent());
        return couponId;
    }
}
