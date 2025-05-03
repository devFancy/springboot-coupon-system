package dev.be.coupon.api.coupon.domain;

import deb.be.coupon.CouponStatus;
import deb.be.coupon.CouponType;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;
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
                LocalDateTime.now()
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
                        CouponType.BURGER,
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
                        LocalDateTime.now()
                )
        ).isInstanceOf(InvalidCouponException.class)
                .hasMessage("쿠폰 관련 부분에서 예외가 발생했습니다.");
    }
}
