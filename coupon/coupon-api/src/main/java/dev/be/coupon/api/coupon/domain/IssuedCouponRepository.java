package dev.be.coupon.api.coupon.domain;

import java.util.UUID;

public interface IssuedCouponRepository {

    boolean existsByUserIdAndCouponId(final UUID userId, final UUID couponId);

    int countByCouponId(final UUID couponId);
    IssuedCoupon save(final IssuedCoupon issuedCoupon);
}
