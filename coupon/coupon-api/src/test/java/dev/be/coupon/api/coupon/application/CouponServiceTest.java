package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.FakeUserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;

class CouponServiceTest {

    private CouponService couponService;
    private FakeUserRoleChecker userRoleChecker;

    @BeforeEach
    void setUp() {
        CouponRepository couponRepository = new InMemoryCouponRepository();
        userRoleChecker = new FakeUserRoleChecker();
        userRoleChecker.updateIsAdmin(true);
        couponService = new CouponService(couponRepository, userRoleChecker);
    }

    @DisplayName("쿠폰을 생성한다.")
    @Test
    void success_coupon() {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(
                "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        // when
        final CouponCreateResult actual = couponService.create(userID, expected);

        // then
        assertThat(actual.id()).isNotNull();
        assertThat(actual.name()).isEqualTo(expected.name());
    }

    @DisplayName("쿠폰을 생성할 때 기존 유형에 없으면 예외가 발생한다.")
    @ParameterizedTest(name = "쿠폰 유형: {0}")
    @ValueSource(strings = {"KHICKEN", "BIZZA", "VURGER"})
    void should_throw_exception_when_coupon_type_is_invalid(final String type) {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(
                "치킨", type, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );

        // when & then
        assertThatThrownBy(() -> couponService.create(userID, expected))
                .isInstanceOf(InvalidCouponTypeException.class)
                .hasMessage("쿠폰 타입이 지정되지 않았습니다.");
    }

    @DisplayName("쿠폰을 생성할 때 관리자 권한이 아니라면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_user_is_not_admin() {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(
                "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );
        userRoleChecker.updateIsAdmin(false); // 관리자 권한이 아닌 경우: false

        // when & then
        assertThatThrownBy(() -> couponService.create(userID, expected))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("권한이 없습니다.");
    }
}
