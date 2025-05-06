package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.domain.IssuedCoupon;
import dev.be.coupon.api.coupon.domain.IssuedCouponRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIssuedCouponRepository implements IssuedCouponRepository {

    private final Map<UUID, IssuedCoupon> issuedCoupons = new ConcurrentHashMap<>();

    @Override
    public boolean existsByUserIdAndCouponId(final UUID userId, final UUID couponId) {
        return issuedCoupons.values().stream()
                .anyMatch(ic -> ic.getUserId().equals(userId) && ic.getCouponId().equals(couponId));
    }

    @Override
    public int countByCouponId(final UUID couponId) {
        return (int) issuedCoupons.values().stream()
                .filter(ic -> ic.getCouponId().equals(couponId))
                .count();
    }

    @Override
    public IssuedCoupon save(final IssuedCoupon issuedCoupon) {
        issuedCoupons.put(issuedCoupon.getId(), issuedCoupon);
        return issuedCoupon;
    }
}
