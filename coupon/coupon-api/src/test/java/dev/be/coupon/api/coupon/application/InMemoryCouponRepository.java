package dev.be.coupon.api.coupon.application;


import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;

import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<Coupon> findAllById(final Iterable<UUID> ids) {
        List<Coupon> result = new ArrayList<>();
        ids.forEach(id -> {
            Coupon coupon = coupons.get(id);
            if (coupon != null) {
                result.add(coupon);
            }
        });
        return result;
    }
}
