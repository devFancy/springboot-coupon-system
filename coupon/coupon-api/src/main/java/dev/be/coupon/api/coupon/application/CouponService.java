package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;

public interface CouponService {

    CouponIssueRequestResult issue(final CouponIssueCommand command);

    CouponCreateResult create(final CouponCreateCommand command);

    CouponUsageResult usage(final CouponUsageCommand command);
}
