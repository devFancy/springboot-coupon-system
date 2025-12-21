package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static dev.be.coupon.domain.coupon.CouponFixtures.정상_쿠폰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class CouponIssueServiceTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("정상적인 요청 시 쿠폰이 발급된다.")
    void should_save_issued_coupon_successfully() {
        // given
        final Coupon coupon = createCoupon();
        final UUID userId = UUID.randomUUID();

        // when
        couponIssueService.issue(userId, coupon.getId());

        // then
        boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId());
        assertThat(isIssued).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 ID 요청 시 예외가 발생한다.")
    void should_throw_exception_when_coupon_does_not_exist() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID nonExistentCouponId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> couponIssueService.issue(userId, nonExistentCouponId))
                .isInstanceOf(CouponConsumerException.class)
                .hasMessage(ErrorType.COUPON_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("유효기간이 지난 쿠폰 요청 시 예외가 발생한다.")
    void should_throw_exception_when_issuing_expired_coupon() {
        // given
        Coupon coupon = 정상_쿠폰(100);

        // NOTE: 리플렉션을 사용하여 강제로 만료일(expiredAt)을 '과거'로 변경
        ReflectionTestUtils.setField(coupon, "expiredAt", LocalDateTime.now().minusDays(1));
        couponRepository.save(coupon);
        final UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> couponIssueService.issue(userId, coupon.getId()))
                .isInstanceOf(CouponConsumerException.class)
                .hasMessage(ErrorType.COUPON_STATUS_IS_NOT_ACTIVE.getMessage());
    }

    @Test
    @DisplayName("이미 발급받은 유저가 재요청 시 예외가 발생한다.")
    void should_throw_exception_when_user_already_has_coupon() {
        // given
        Coupon coupon = createCoupon();
        UUID userId = UUID.randomUUID();

        // 1차 발급 (성공)
        couponIssueService.issue(userId, coupon.getId());

        // when & then (2차 발급 시도)
        assertThatThrownBy(() -> couponIssueService.issue(userId, coupon.getId()))
                .isInstanceOf(CouponConsumerException.class)
                .hasMessage(ErrorType.COUPON_ALREADY_ISSUED.getMessage());
    }

    private Coupon createCoupon() {
        Coupon coupon = 정상_쿠폰(100);
        return couponRepository.save(coupon);
    }
}
