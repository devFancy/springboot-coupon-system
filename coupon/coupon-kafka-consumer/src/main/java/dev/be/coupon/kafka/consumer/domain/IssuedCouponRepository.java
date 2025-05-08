package dev.be.coupon.kafka.consumer.domain;

public interface IssuedCouponRepository {
    IssuedCoupon save(final IssuedCoupon issuedCoupon);
}
