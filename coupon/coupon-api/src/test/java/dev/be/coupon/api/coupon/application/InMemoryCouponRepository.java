package dev.be.coupon.api.coupon.application;


import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCouponRepository implements CouponRepository {

    private final Map<UUID, Coupon> coupons = new ConcurrentHashMap<>();

    @Override
    public Coupon save(final Coupon coupon) {
        coupons.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(final UUID couponId) {
        return Optional.ofNullable(coupons.get(couponId));
    }
}
