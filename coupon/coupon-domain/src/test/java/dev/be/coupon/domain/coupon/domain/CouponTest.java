package dev.be.coupon.domain.coupon.domain;

import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.exception.InvalidCouponException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponPeriodException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponQuantityException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class CouponTest {

    @DisplayName("쿠폰을 발급한다.")
    @Test
    void create_coupon() {
        // given & when
        Coupon coupon = new Coupon(
                "쿠폰 이름",
                CouponType.BURGER,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );

        // then
        assertThat(coupon.getId()).isNotNull();
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @DisplayName("쿠폰 이름이 존재하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_name_is_null() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        null,
                        CouponType.CHICKEN,
                        10,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponException.class)
                .hasMessage("쿠폰 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("쿠폰 타입이 존재하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_type_is_null() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "쿠폰 이름",
                        null,
                        10,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponException.class)
                .hasMessage("쿠폰 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("쿠폰 생성시 발급 가능한 총 수량이 1보다 작으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_totalQuantity_less_then_1() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "쿠폰 이름",
                        CouponType.CHICKEN,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(7)
                )
        ).isInstanceOf(InvalidCouponQuantityException.class)
                .hasMessage("쿠폰 발급 수량은 1 이상이야 합니다.");
    }

    @DisplayName("쿠폰 유효기간을 올바르게 설정하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_validPeriod_is_invalid() {
        // given & when & then
        assertThatThrownBy(() -> new Coupon(
                        "쿠폰 이름",
                        CouponType.CHICKEN,
                        10,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )
        ).isInstanceOf(InvalidCouponPeriodException.class)
                .hasMessage("쿠폰 유효기간이 잘못되었습니다.");
    }

    @DisplayName("현재 시간이 유효기간 내에 있으면 쿠폰 상태는 ACTIVE이다.")
    @Test
    void update_coupon_status_to_active() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("쿠폰", CouponType.PIZZA, 10, now.minusDays(1), now.plusDays(1));

        // when
        coupon.validateIssuable(now);

        // then
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }
}
