package dev.be.coupon.api.coupon.application.v1;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;

public interface CouponServiceV1 {
    CouponIssueResult issue(final CouponIssueCommand command);
}
