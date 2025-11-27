package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.be.coupon.domain.coupon.FailedIssuedCouponFixtures.실패한_쿠폰_이력;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailedIssuedCouponTest {

    @DisplayName("쿠폰 발급 실패 이력을 생성하면 초기 상태(재시도 0회, 미해결)로 생성된다.")
    @Test
    void success_create_failed_issued_coupon() {
        // given & when
        FailedIssuedCoupon failedCoupon = 실패한_쿠폰_이력();

        // then
        assertThat(failedCoupon.getId()).isNotNull();
        assertThat(failedCoupon.getFailedAt()).isNotNull();
        assertThat(failedCoupon.getRetryCount()).isZero(); // 초기값 0
        assertThat(failedCoupon.isResolved()).isFalse();   // 초기값 false
    }

    @DisplayName("재시도 횟수를 증가시키면 카운트가 1 증가한다.")
    @Test
    void success_increase_retry_count() {
        // given
        FailedIssuedCoupon failedCoupon = 실패한_쿠폰_이력();
        int initialCount = failedCoupon.getRetryCount();

        // when
        failedCoupon.increaseRetryCount();

        // then
        assertThat(failedCoupon.getRetryCount()).isEqualTo(initialCount + 1);
    }

    @DisplayName("발급 실패 건을 해결 처리하면 해결 상태가 true로 변경된다.")
    @Test
    void success_mark_resolved() {
        // given
        FailedIssuedCoupon failedCoupon = 실패한_쿠폰_이력();

        // when
        failedCoupon.markResolved();

        // then
        assertThat(failedCoupon.isResolved()).isTrue();
    }

    @DisplayName("사용자 ID나 쿠폰 ID가 없으면 실패 이력 생성에 실패한다.")
    @Test
    void fail_create_when_id_is_null() {
        assertThatThrownBy(() -> new FailedIssuedCoupon(null, UUID.randomUUID()))
                .isInstanceOf(CouponDomainException.class);
    }
}