package dev.be.coupon.domain.coupon;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {

    Coupon save(final Coupon coupon);

    Optional<Coupon> findById(final UUID couponId);
}
