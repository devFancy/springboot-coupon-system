package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static dev.be.coupon.domain.coupon.CouponFixtures.만료된_쿠폰;
import static dev.be.coupon.domain.coupon.CouponFixtures.정상_쿠폰;
import static dev.be.coupon.domain.coupon.CouponFixtures.쿠폰_이름이_존재하지_않음;
import static dev.be.coupon.domain.coupon.CouponFixtures.쿠폰_총_발급_수량이_0보다_작은경우;
import static dev.be.coupon.domain.coupon.CouponFixtures.쿠폰_할인_유형이_존재하지_않음;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @DisplayName("쿠폰을 생성한다.")
    @Test
    void should_create_coupon_successfully() {
        // given & when
        Coupon coupon = 정상_쿠폰();

        // then
        assertThat(coupon.getId()).isNotNull();
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @DisplayName("쿠폰 이름이 존재하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_name_is_null() {
        // given & when & then
        assertThatThrownBy(() -> 쿠폰_이름이_존재하지_않음()
        ).isInstanceOf(CouponDomainException.class)
                .hasMessage("쿠폰 이름이 존재해야 합니다.");
    }

    @DisplayName("쿠폰 할인 타입이 존재하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_discount_type_is_null() {
        // given & when & then
        assertThatThrownBy(() -> 쿠폰_할인_유형이_존재하지_않음()
        ).isInstanceOf(CouponDomainException.class)
                .hasMessage("쿠폰 생성에 필요한 정보가 누락되었습니다.");
    }

    @DisplayName("쿠폰 생성시 발급 가능한 총 수량이 1보다 작으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_total_quantity_is_less_than_one() {
        // given & when & then
        assertThatThrownBy(() -> 쿠폰_총_발급_수량이_0보다_작은경우()
        ).isInstanceOf(CouponDomainException.class)
                .hasMessage("쿠폰 발급 수량은 1 이상이야 합니다.");
    }

    @DisplayName("쿠폰 유효기간을 올바르게 설정하지 않으면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_expired_at_is_past() {
        // given & when & then
        assertThatThrownBy(() -> 만료된_쿠폰()
        ).isInstanceOf(CouponDomainException.class)
                .hasMessage("ACTIVE 상태 쿠폰의 만료일은 현재 시간보다 이후여야 합니다.");
    }

    @DisplayName("정확히 만료 시점에는 쿠폰은 여전히 ACTIVE 상태이다.")
    @Test
    void should_be_active_status_at_exact_expiration_time() {
        // given
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(10);
        Coupon coupon = new Coupon(
                "쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED, BigDecimal.valueOf(10_000L),
                100,
                expiredAt);

        // when & then
        assertThatCode(() -> coupon.validateStatusIsActive(expiredAt))
                .doesNotThrowAnyException();
        assertThat(coupon.getCouponStatus()).isEqualTo(CouponStatus.ACTIVE);
    }
}
