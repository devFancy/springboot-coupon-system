package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static dev.be.coupon.domain.coupon.IssuedCouponFixtures.발급된_쿠폰;
import static dev.be.coupon.domain.coupon.IssuedCouponFixtures.사용된_쿠폰;
import static dev.be.coupon.domain.coupon.IssuedCouponFixtures.사용자ID가_없는_쿠폰;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssuedCouponTest {
    @DisplayName("발급된 쿠폰을 생성할 수 있다.")
    @Test
    void should_create_issued_coupon_successfully() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        final IssuedCoupon issuedCoupon = 발급된_쿠폰(userId, couponId);

        // when & then
        assertNotNull(issuedCoupon.getId());
        assertEquals(userId, issuedCoupon.getUserId());
        assertEquals(couponId, issuedCoupon.getCouponId());
    }

    @DisplayName("사용자 ID가 없으면 발급된 쿠폰을 생성할 수 없다.")
    @Test
    void should_throw_exception_when_user_id_is_null() {
        // given
        // when & then
        assertThatThrownBy(() -> 사용자ID가_없는_쿠폰())
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("발급된 쿠폰 생성에 필요한 정보가 누락되었습니다.");
    }

    @DisplayName("발급된 쿠폰을 사용 처리할 수 있다.")
    @Test
    void should_mark_as_used_when_using_coupon() {
        final IssuedCoupon coupon = 발급된_쿠폰(UUID.randomUUID(), UUID.randomUUID());
        final LocalDateTime usedAt = LocalDateTime.now();

        coupon.use(usedAt);

        assertTrue(coupon.isUsed());
        assertEquals(usedAt, coupon.getUsedAt());
    }

    @DisplayName("이미 사용된 쿠폰은 다시 사용할 수 없다.")
    @Test
    void should_throw_exception_when_already_used_coupon_is_reused() {
        final IssuedCoupon coupon = 사용된_쿠폰();

        assertThatThrownBy(() -> coupon.use(LocalDateTime.now().plusMinutes(1)))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }
}
