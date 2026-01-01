package dev.be.coupon.domain.coupon;

public interface CouponIssueFailedEventRepository {

    CouponIssueFailedEvent save(final CouponIssueFailedEvent couponIssueFailedEvent);
}
