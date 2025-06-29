package dev.be.coupon.api.coupon.application.v2;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;

public interface AsyncCouponService {

    CouponIssueRequestResult issue(final CouponIssueCommand command);
}
