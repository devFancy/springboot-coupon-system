package dev.be.coupon.api.coupon.application.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class CouponIssueException extends CouponException {

    public CouponIssueException(final Object data) {
        super(ErrorType.COUPON_ISSUE_PROCESSING_ERROR, data);
    }
}
