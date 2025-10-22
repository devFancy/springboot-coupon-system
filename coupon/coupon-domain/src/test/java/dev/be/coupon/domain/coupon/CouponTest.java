package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.InvalidCouponException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponPeriodException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponQuantityException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class CouponTest {

    @DisplayName("쿠폰을 생성한다.")
    @Test
    void success_create_coupon() {
        // given & when
        Coupon coupon = new Coupon(
                "선착순 쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED,
                BigDecimal.valueOf(10_000L),
                100,
                LocalDateTime.now().plusDays(7)
        );

        // then
        assertThat(coupon.getId()).isNotNull();
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @DisplayName("쿠폰 이름이 존재하지 않으면 예외가 발생한다.")
    @Test
    void fail_should_throw_exception_when_coupon_name_is_null() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        null,
                        CouponType.BURGER,
                        CouponDiscountType.FIXED,
                        BigDecimal.valueOf(10_000L),
                        100,
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponException.class)
                .hasMessage("쿠폰 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("쿠폰 할인 타입이 존재하지 않으면 예외가 발생한다.")
    @Test
    void fail_should_throw_exception_when_coupon_discount_type_is_null() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "선착순 쿠폰",
                        CouponType.BURGER,
                        null,
                        BigDecimal.valueOf(10_000L),
                        100,
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponException.class)
                .hasMessage("쿠폰 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("쿠폰 생성시 발급 가능한 총 수량이 1보다 작으면 예외가 발생한다.")
    @Test
    void fail_should_throw_exception_when_coupon_totalQuantity_less_then_1() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "선착순 쿠폰",
                        CouponType.BURGER,
                        CouponDiscountType.FIXED,
                        BigDecimal.valueOf(10_000L),
                        0,
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponQuantityException.class)
                .hasMessage("쿠폰 발급 수량은 1 이상이야 합니다.");
    }

    @DisplayName("쿠폰 유효기간을 올바르게 설정하지 않으면 예외가 발생한다.")
    @Test
    void fail_should_throw_exception_when_coupon_expiredAt_is_invalid() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "선착순 쿠폰",
                        CouponType.BURGER,
                        CouponDiscountType.FIXED,
                        BigDecimal.valueOf(10_000L),
                        100,
                        LocalDateTime.now().minusDays(1)
                )
        ).isInstanceOf(InvalidCouponPeriodException.class)
                .hasMessage("쿠폰 유효기간이 잘못되었습니다.");
    }

    @DisplayName("정확히 만료 시점에는 쿠폰은 여전히 ACTIVE 상태이다.")
    @Test
    void success_update_coupon_status_to_active() {
        // given
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(10);
        Coupon coupon = new Coupon("쿠폰", CouponType.BURGER, CouponDiscountType.FIXED, BigDecimal.valueOf(10_000L), 100, expiredAt);

        // when & then
        assertThatCode(() -> coupon.validateIssuableStatus(expiredAt))
                .doesNotThrowAnyException();
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }
}
