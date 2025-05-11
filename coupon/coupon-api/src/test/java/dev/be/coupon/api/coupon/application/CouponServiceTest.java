package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.exception.InvalidIssuedCouponException;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.FakeUserRoleChecker;
import dev.be.coupon.api.coupon.domain.IssuedCouponRepository;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import dev.be.coupon.api.coupon.infrastructure.kafka.dto.CouponIssueMessage;
import dev.be.coupon.api.coupon.infrastructure.kafka.producer.CouponIssueProducer;
import dev.be.coupon.api.coupon.infrastructure.redis.AppliedUserRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCacheRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCountRedisRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

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

    @DisplayName("동일한 사용자에게 쿠폰을 1000번 발급 요청해도 중복 발급은 1회만 된다. - 단일 스레드")
    @Test
    void should_only_issue_once_for_same_user() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(userId, "햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(command);
        final UUID couponId = result.id();

        // when
        for (int i = 0; i < 1000; i++) {
            try {
                couponService.issue(new CouponIssueCommand(userId, couponId));
            } catch (InvalidIssuedCouponException ignore) {
                // 중복 쿠폰 발급 무시
            }
        }

        Thread.sleep(10000);

        // then
        // Redis 중복 발급 방지 키 확인
        final String key = "applied_user:" + couponId;
        final Long size = redisTemplate.opsForSet().size(key);
        assertThat(size).isEqualTo(1);

        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
        System.out.println("최종 쿠폰 발급 수: " + issuedCouponRepository.countByCouponId(couponId));
    }

    @DisplayName("동일한 사용자에게 쿠폰을 1,000,000번 동시 발급 요청해도 중복 발급은 1회만 된다. - 멀티 스레드")
    @Test
    void should_only_issue_once_for_same_user_multi_thread() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(userId, "햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(command);
        final UUID couponId = result.id();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(1000000);

        // when
        for (int i = 0; i < 1000000; i++) {
            executor.execute(() -> {
                try {
                    couponService.issue(new CouponIssueCommand(userId, couponId));
                } catch (InvalidIssuedCouponException ignore) {
                    // 중복 쿠폰 발급 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Thread.sleep(10000);

        // then
        final String appliedKey = "applied_user:" + couponId;
        final Long userSetSize = redisTemplate.opsForSet().size(appliedKey);
        assertThat(userSetSize).isEqualTo(1); // Redis 기반 중복 방지 확인

        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isLessThanOrEqualTo(1);
        System.out.println("최종 쿠폰 발급 수: " + issuedCouponRepository.countByCouponId(couponId));
    }

    @DisplayName("1,000명의 사용자에게 수량 500개짜리 쿠폰을 발급하면 최대 500개만 발급된다. - 단일 스레드")
    @Test
    void should_only_issue_up_to_total_quantity_limit() throws InterruptedException {
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
            } catch (InvalidIssuedCouponException ignore) {
                // 수량 초과 예외 무시
            }
        }

        Thread.sleep(10000);

        // then

        final String countKey = "coupon_count:" + couponId;
        final String count = redisTemplate.opsForValue().get(countKey);
        assertThat(count).isEqualTo("500");

        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(500);
        System.out.println("최종 쿠폰 발급 수: " + issuedCouponRepository.countByCouponId(couponId));
    }

    @DisplayName("1,000,000명의 사용자에게 동시 발급 요청 시 수량 500개가 초과 발급되지 않는다. - 멀티 스레드")
    @Test
    void should_only_issue_up_to_total_quantity_limit_multithreaded() throws InterruptedException {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand(adminId, "피자 쿠폰", "PIZZA", 500, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final UUID couponId = couponService.create(command).id();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(1000000);

        // when
        for (int i = 0; i < 1000000; i++) {
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

        Thread.sleep(10000);

        // then

        final String countKey = "coupon_count:" + couponId;
        final String count = redisTemplate.opsForValue().get(countKey);
        assertThat(count).isEqualTo("500");

        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(500);
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isLessThanOrEqualTo(500);
        System.out.println("최종 쿠폰 발급 수: " + issuedCouponRepository.countByCouponId(couponId));
    }

    @DisplayName("쿠폰 수량 초과 시 쿠폰 발급 수량이 롤백된다.")
    @Test
    void should_decrement_count_when_quantity_exceeded() throws InterruptedException {
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
        } catch (InvalidIssuedCouponException ignore) {
        }

        Thread.sleep(5000);

        // then
        final String countKey = "coupon_count:" + couponId;
        final Long count = Long.parseLong(Objects.requireNonNull(redisTemplate.opsForValue().get(countKey)));
        assertThat(count).isEqualTo(1); // 롤백이 되지 않으면 2가 됨
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
    }

    @DisplayName("Kafka 메시지가 전송된다.")
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

    @DisplayName("Kafka 메시지가 100명의 사용자에 대해 전송된다.")
    @Test
    void should_send_kafka_message_for_100_users() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand createCommand = new CouponCreateCommand(
                adminId, "100명 대상 테스트 쿠폰", "CHICKEN", 100, LocalDateTime.now(), LocalDateTime.now().plusDays(1)
        );
        final UUID couponId = couponService.create(createCommand).id();

        // when
        for (int i = 0; i < 100; i++) {
            final UUID userId = UUID.randomUUID(); // 100명의 서로 다른 사용자
            try {
                final CouponIssueResult result = couponService.issue(new CouponIssueCommand(userId, couponId));
                assertThat(result.userId()).isEqualTo(userId);
                assertThat(result.couponId()).isEqualTo(couponId);
            } catch (InvalidIssuedCouponException e) {
                // 수량 초과가 발생하지 않도록 생성 수량을 100으로 지정했기 때문에 무시 X
                throw new AssertionError("예상치 못한 발급 실패 발생: " + e.getMessage());
            }
        }

        // then
        String redisKey = "coupon_count:" + couponId;
        String countValue = redisTemplate.opsForValue().get(redisKey);
        assertThat(countValue).isEqualTo("100");

        System.out.println("Kafka 메시지 100건 발송 및 Redis 발급 수량 확인 완료");
    }
}
