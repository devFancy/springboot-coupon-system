package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.auth.application.AuthService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.exception.CouponAlreadyUsedException;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.CouponEntryRedisCounter;
import dev.be.coupon.infra.redis.CouponRedisCache;
import dev.be.coupon.infra.redis.CouponRedisDuplicateValidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CouponServiceImplTest 주의사항:
 * <p>
 * 이 테스트는 Kafka Consumer 가 실제 DB(JPA Repository)에 접근해 데이터를 저장하는 구조이므로,
 * 테스트에서도 Spring Context 에서 관리하는 실제 JPA 기반 Repository 를 사용해야 합니다.
 * <p>
 * 해당 테스트를 하기 위한 사전 준비 사항
 * - Docker Compose 실행 - MySQL, Redis, Kafka 구동되어야 함
 * - Kafka Application 실행
 */
@ActiveProfiles("test")
@SpringBootTest
class CouponServiceImplTest {

    private CouponServiceImpl couponServiceImpl;

    @MockBean
    private AuthService authService;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private CouponRedisDuplicateValidate couponRedisDuplicateValidate;

    @Autowired
    private CouponEntryRedisCounter couponEntryRedisCounter;

    @Autowired
    private CouponRedisCache couponRedisCache;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @BeforeEach
    void setUp() {
        CouponIssueProducer couponIssueProducer = new CouponIssueProducer(kafkaTemplate);

        couponServiceImpl = new CouponServiceImpl(
                couponRepository,
                issuedCouponRepository,
                couponRedisDuplicateValidate,
                couponEntryRedisCounter,
                couponRedisCache,
                couponIssueProducer,
                authService
        );
    }

    @AfterEach
    void tearDown() {
        safeDelete(stringRedisTemplate, "coupon:entry_count:*");
        safeDelete(stringRedisTemplate, "coupon:wait_queue:*");
        safeDelete(stringRedisTemplate, "coupon:meta:*");

        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    private <T> void safeDelete(final RedisTemplate<String, T> template, final String pattern) {
        Set<String> keys = template.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            template.delete(keys);
        }
    }

    // ==================== Create Coupon Tests ====================

    @DisplayName("관리자가 쿠폰을 생성한다.")
    @Test
    void success_create_coupon() {
        // given
        final UUID adminId = UUID.randomUUID();
        given(authService.isAdmin(adminId)).willReturn(true);
        final CouponCreateCommand command = new CouponCreateCommand(adminId, "치킨 할인 쿠폰", "CHICKEN", 100, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when
        final CouponCreateResult result = couponServiceImpl.create(command);

        // then
        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo(command.name());
        assertThat(couponRepository.findById(result.id())).isPresent();
    }

    @DisplayName("사용자가 쿠폰 발급을 요청하면 성공적으로 접수된다.")
    @Test
    void success_issue_request() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon("테스트 쿠폰", 10);
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        CouponIssueRequestResult result = couponServiceImpl.issue(command);

        // then
        assertThat(result).isEqualTo(CouponIssueRequestResult.SUCCESS);
    }

    @DisplayName("쿠폰을 생성할 때 관리자 권한이 아니라면 예외가 발생한다.")
    @Test
    void fail_create_coupon_when_not_admin() {
        // given
        final UUID userId = UUID.randomUUID();
        given(authService.isAdmin(userId)).willReturn(false);
        final CouponCreateCommand command = new CouponCreateCommand(userId, "치킨 할인 쿠폰", "CHICKEN", 100, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.create(command))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("권한이 없습니다.");
    }

    // ==================== Issue Coupon Tests ====================

    @DisplayName("동일한 사용자가 중복으로 쿠폰 발급을 요청하면 'DUPLICATE'를 반환한다.")
    @Test
    void fail_issue_request_due_to_duplicate_entry() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon("중복 테스트 쿠폰", 10);
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        couponServiceImpl.issue(command);
        CouponIssueRequestResult secondResult = couponServiceImpl.issue(command);

        // then
        assertThat(secondResult).isEqualTo(CouponIssueRequestResult.DUPLICATE);

        // 최종적으로 DB에 1개만 저장되었는지 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1L)
        );
    }

    @DisplayName("쿠폰이 모두 소진된 후 추가 발급을 요청하면 'SOLD_OUT'을 반환한다.")
    @Test
    void fail_issue_request_due_to_sold_out() {
        // given
        final int totalQuantity = 1;
        final UUID couponId = createCoupon("소진 테스트 쿠폰", totalQuantity);
        final UUID user1 = UUID.randomUUID();
        final UUID user2 = UUID.randomUUID();

        // when
        CouponIssueRequestResult result1 = couponServiceImpl.issue(new CouponIssueCommand(user1, couponId));
        CouponIssueRequestResult result2 = couponServiceImpl.issue(new CouponIssueCommand(user2, couponId));

        // then
        assertThat(result1).isEqualTo(CouponIssueRequestResult.SUCCESS);
        assertThat(result2).isEqualTo(CouponIssueRequestResult.SOLD_OUT);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(totalQuantity)
        );
    }

    @DisplayName("선착순 이벤트로 동시에 요청해도 쿠폰 총 수량만 발급되도록 한다.")
    @Test
    void issue_request_multiThreaded_success() throws InterruptedException {
        // given
        final int totalQuantity = 100;
        final UUID couponId = createCoupon("동시성 테스트 쿠폰", totalQuantity);

        final int threadCount = 100;
        final int requestCount = 10000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            final UUID userId = UUID.randomUUID();
            executorService.submit(() -> {
                try {
                    CouponIssueRequestResult result = couponServiceImpl.issue(new CouponIssueCommand(userId, couponId));
                    if (result == CouponIssueRequestResult.SUCCESS) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        Thread.sleep(10000);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(totalQuantity);
    }

    // ==================== Usage Coupon Tests ====================

    @DisplayName("쿠폰을 사용하면 사용 처리된다.")
    @Test
    void success_coupon_usage() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();
        final UUID couponId = createAndIssueCoupon(adminId, userId, 10, now.minusDays(1), now.plusDays(1));

        // when
        final CouponUsageResult result = couponServiceImpl.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isTrue();
        assertThat(result.usedAt()).isNotNull();
    }

    @DisplayName("발급받지 않은 쿠폰을 사용하려고 하면 예외가 발생한다.")
    @Test
    void fail_usage_when_coupon_not_issued() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID otherUserId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();
        final UUID couponId = createAndIssueCoupon(adminId, userId, 10, now.minusDays(1), now.plusDays(1));

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(otherUserId, couponId)))
                .isInstanceOf(IssuedCouponNotFoundException.class)
                .hasMessage("발급되지 않았거나 소유하지 않은 쿠폰입니다.");
    }

    @DisplayName("이미 사용한 쿠폰을 다시 사용하면 예외가 발생한다.")
    @Test
    void fail_usage_when_coupon_already_used() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();
        final UUID couponId = createAndIssueCoupon(adminId, userId, 1, now.minusDays(1), now.plusDays(1));

        couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)); // 첫 번째 사용

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }

    private UUID createCoupon(String name, int totalQuantity) {
        final UUID adminId = UUID.randomUUID();

        given(authService.isAdmin(adminId)).willReturn(true);

        CouponCreateCommand command = new CouponCreateCommand(
                adminId,
                name,
                "CHICKEN", totalQuantity,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10)
        );
        final UUID couponId = couponServiceImpl.create(command).id();
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        couponRedisCache.save(coupon);

        return couponId;
    }

    private UUID createAndIssueCoupon(final UUID adminId, final UUID userId,
                                      final int quantity,
                                      final LocalDateTime validFrom, final LocalDateTime validUntil) {
        // 1. 쿠폰 생성
        given(authService.isAdmin(adminId)).willReturn(true);
        final UUID couponId = couponServiceImpl.create(new CouponCreateCommand(
                adminId, "테스트용 쿠폰", "CHICKEN", quantity, validFrom, validUntil
        )).id();

        // 2. 쿠폰 발급 요청
        CouponIssueRequestResult result = couponServiceImpl.issue(new CouponIssueCommand(userId, couponId));
        assertThat(result).isEqualTo(CouponIssueRequestResult.SUCCESS);

        // 3. Kafka Consumer 가 비동기로 쿠폰 발급을 저장하는 동안 대기
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(issuedCouponRepository.findByUserIdAndCouponId(userId, couponId))
                        .isPresent());
        return couponId;
    }
}
