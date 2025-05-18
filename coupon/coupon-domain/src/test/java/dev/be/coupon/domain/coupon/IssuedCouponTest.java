package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponAlreadyUsedException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class IssuedCouponTest {
    @DisplayName("발급된 쿠폰을 생성할 수 있다.")
    @Test
    void create_issue_coupon_success() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        final IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);

        // when & then
        assertNotNull(issuedCoupon.getId());
        assertEquals(userId, issuedCoupon.getUserId());
        assertEquals(couponId, issuedCoupon.getCouponId());
    }

    @DisplayName("사용자 ID가 없으면 발급된 쿠폰을 생성할 수 없다.")
    @Test
    void should_throw_exception_when_userId_is_null() {
        // given
        final UUID id = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final LocalDateTime issuedAt = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new IssuedCoupon(id, null, couponId, false, issuedAt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("발급된 쿠폰 생성에 필요한 정보가 누락되었습니다.");
    }

    @DisplayName("발급된 쿠폰을 사용 처리할 수 있다.")
    @Test
    void should_mark_coupon_as_used_success() {
        final IssuedCoupon coupon = new IssuedCoupon(UUID.randomUUID(), UUID.randomUUID());
        final LocalDateTime usedAt = LocalDateTime.now();

        coupon.use(usedAt);

        assertTrue(coupon.isUsed());
        assertEquals(usedAt, coupon.getUsedAt());
    }

    @DisplayName("이미 사용된 쿠폰은 다시 사용할 수 없다.")
    @Test
    void should_throw_exception_when_reusing_coupon() {
        final IssuedCoupon coupon = new IssuedCoupon(UUID.randomUUID(), UUID.randomUUID());
        final LocalDateTime usedAt = LocalDateTime.now();
        coupon.use(usedAt);

        assertThatThrownBy(() -> coupon.use(LocalDateTime.now().plusMinutes(1)))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }
}
