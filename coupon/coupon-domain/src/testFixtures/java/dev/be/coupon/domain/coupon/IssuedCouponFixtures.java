package dev.be.coupon.domain.coupon;

import java.time.LocalDateTime;
import java.util.UUID;

public class IssuedCouponFixtures {

    public static final UUID 사용자_ID = UUID.randomUUID();
    public static final UUID 쿠폰_ID = UUID.randomUUID();
    public static final LocalDateTime 발급_일자 = LocalDateTime.now().minusDays(1);

    public static IssuedCoupon 발급된_쿠폰() {
        return new IssuedCoupon(
                사용자_ID,
                쿠폰_ID
        );
    }

    public static IssuedCoupon 발급된_쿠폰(final UUID userId, final UUID couponId) {
        return new IssuedCoupon(
                userId,
                couponId
        );
    }

    public static IssuedCoupon 사용된_쿠폰() {
        return new IssuedCoupon(
                UUID.randomUUID(),
                사용자_ID,
                쿠폰_ID,
                true,
                발급_일자,
                LocalDateTime.now()
        );
    }

    public static IssuedCoupon 사용자ID가_없는_쿠폰() {
        return new IssuedCoupon(
                UUID.randomUUID(),
                null,
                쿠폰_ID,
                false,
                발급_일자,
                null
        );
    }
}
