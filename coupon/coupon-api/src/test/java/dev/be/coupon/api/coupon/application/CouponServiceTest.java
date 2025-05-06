package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

class CouponServiceTest {

    private CouponService couponService;
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
        couponService = new CouponService(couponRepository);
    }

    @DisplayName("쿠폰을 발급한다.")
    @Test
    void success_coupon() {
        // given
        final CouponCreateCommand expected = new CouponCreateCommand(
                "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        // when
        final CouponCreateResult actual = couponService.create(expected);

        // then
        assertThat(actual.id()).isNotNull();
        assertThat(actual.name()).isEqualTo(expected.name());
    }

    @DisplayName("쿠폰을 발급할 때 기존 유형에 없으면 예외가 발생한다.")
    @ParameterizedTest(name = "쿠폰 유형: {0}")
    @ValueSource(strings = {"KHICKEN", "BIZZA", "VURGER"})
    void should_throw_exception_when_coupon_type_is_invalid(final String type) {
        // given
        final CouponCreateCommand expected = new CouponCreateCommand(
                "치킨", type, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        // when & then
        assertThatThrownBy(() -> couponService.create(expected))
                .isInstanceOf(InvalidCouponTypeException.class)
                .hasMessage("쿠폰 타입이 지정되지 않았습니다.");
    }
}
