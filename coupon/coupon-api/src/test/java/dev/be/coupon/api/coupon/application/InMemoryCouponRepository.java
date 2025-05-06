package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryCouponRepository implements CouponRepository {

    private final Map<UUID, Coupon> coupons = new HashMap<>();

    @Override
    public Coupon save(final Coupon coupon) {
        coupons.put(coupon.getId(), coupon);
        return coupon;
    }
}
