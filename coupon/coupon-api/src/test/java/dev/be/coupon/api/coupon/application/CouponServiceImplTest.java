package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.exception.CouponAlreadyUsedException;
import dev.be.coupon.domain.coupon.exception.CouponNotCurrentlyUsableException;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Autowired
    private CouponServiceImpl couponServiceImpl;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @DisplayName("사용자가 쿠폰 발급을 요청하면 성공적으로 접수된다.")
    @Test
    void success_issue_request() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(100).getId();
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        CouponIssueRequestResult result = couponServiceImpl.issue(command);

        // then
        assertThat(result).isEqualTo(CouponIssueRequestResult.SUCCESS);
    }

    @DisplayName("동일한 사용자가 중복으로 쿠폰 발급을 요청하면 'DUPLICATE' 를 반환한다.")
    @Test
    void fail_issue_request_due_to_duplicate_entry() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(10).getId();
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        couponServiceImpl.issue(command);
        CouponIssueRequestResult secondResult = couponServiceImpl.issue(command);

        // then
        assertThat(secondResult).isEqualTo(CouponIssueRequestResult.DUPLICATE);
    }

    @DisplayName("쿠폰이 모두 소진된 후 추가 발급을 요청하면 'SOLD_OUT'을 반환한다.")
    @Test
    void fail_issue_request_due_to_sold_out() {
        // given
        final UUID couponId = createCoupon(1).getId();
        final UUID user1 = UUID.randomUUID();
        final UUID user2 = UUID.randomUUID();

        // when
        CouponIssueRequestResult result1 = couponServiceImpl.issue(new CouponIssueCommand(user1, couponId));
        CouponIssueRequestResult result2 = couponServiceImpl.issue(new CouponIssueCommand(user2, couponId));

        // then
        assertThat(result1).isEqualTo(CouponIssueRequestResult.SUCCESS);
        assertThat(result2).isEqualTo(CouponIssueRequestResult.SOLD_OUT);
    }

    @DisplayName("선착순 쿠폰 이벤트로 여러명의 사용자가 동시에 요청해도 쿠폰 총 수량만 발급되도록 한다.")
    @Test
    void success_issue_request_multiThreaded_success() throws InterruptedException {
        // given
        final int totalQuantity = 100;
        final UUID couponId = createCoupon(100).getId();

        final int threadCount = 100;
        final int requestCount = 10000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            final UUID userId = UUID.randomUUID();
            executorService.submit(() -> {
                try {
                    CouponIssueRequestResult result = couponServiceImpl.issue(new CouponIssueCommand(userId, couponId));
                    if (result == CouponIssueRequestResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (result == CouponIssueRequestResult.SOLD_OUT) {
                        soldOutCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(soldOutCount.get()).isEqualTo(requestCount - totalQuantity);
    }

    @DisplayName("사용자가 발급된 쿠폰을 사용하면 사용 처리된다.")
    @Test
    void success_coupon_usage() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(1).getId();
        issueCoupon(userId, couponId);

        // when
        final CouponUsageResult result = couponServiceImpl.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isTrue();
        assertThat(result.usedAt()).isNotNull();
    }

    @DisplayName("사용자가 소유하지 않은 쿠폰을 사용하면 예외가 발생한다.")
    @Test
    void fail_usage_when_coupon_is_not_owned_by_user() {
        // given
        final UUID userA = UUID.randomUUID(); // 쿠폰 소유자
        final UUID userB = UUID.randomUUID(); // 사용 시도자
        final UUID couponId = createCoupon(1).getId();
        issueCoupon(userA, couponId); // 쿠폰은 userA에게 발급됨

        // when & then
        // userB가 userA의 쿠폰을 사용하려고 할 때 예외가 발생해야 함
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userB, couponId)))
                .isInstanceOf(IssuedCouponNotFoundException.class)
                .hasMessage("발급되지 않았거나 소유하지 않은 쿠폰입니다.");
    }

    @DisplayName("사용자가 이미 사용한 쿠폰을 재사용하면 예외가 발생한다.")
    @Test
    void fail_usage_when_coupon_already_used() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(100).getId();
        issueCoupon(userId, couponId);

        couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)); // 첫 번째 사용

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }

    @DisplayName("유효 기간이 만료된 쿠폰을 사용하면 예외가 발생한다.")
    @Test
    void fail_usage_when_coupon_is_expired() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon = new Coupon(
                "만료된 쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED,
                BigDecimal.valueOf(10_000L),
                100,
                now.plusSeconds(1)
        );
        couponRepository.save(expiredCoupon);
        issueCoupon(userId, expiredCoupon.getId());

        Thread.sleep(1100);

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userId, expiredCoupon.getId())))
                .isInstanceOf(CouponNotCurrentlyUsableException.class)
                .hasMessage("현재 쿠폰은 사용 가능한 상태가 아닙니다.");
    }

    private Coupon createCoupon(final int totalQuantity) {
        Coupon coupon = new Coupon(
                "선착순 쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED,
                BigDecimal.valueOf(10_000L),
                totalQuantity,
                LocalDateTime.now().plusDays(7)
        );
        return couponRepository.save(coupon);
    }

    private void issueCoupon(final UUID userId, final UUID couponId) {
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
    }
}
