package dev.be.coupon.api.coupon.application.v1;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;

public interface SyncCouponService {
    CouponIssueResult issue(final CouponIssueCommand command);
}
