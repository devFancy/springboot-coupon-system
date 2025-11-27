package dev.be.coupon.domain.coupon;

import java.util.UUID;

public class FailedIssuedCouponFixtures {

    public static final UUID 사용자_ID = UUID.randomUUID();
    public static final UUID 쿠폰_ID = UUID.randomUUID();

    public static FailedIssuedCoupon 실패한_쿠폰_이력() {
        return new FailedIssuedCoupon(
                사용자_ID,
                쿠폰_ID
        );
    }

    public static FailedIssuedCoupon 실패한_쿠폰_이력(final UUID userId, final UUID couponId) {
        return new FailedIssuedCoupon(
                userId,
                couponId
        );
    }
}
