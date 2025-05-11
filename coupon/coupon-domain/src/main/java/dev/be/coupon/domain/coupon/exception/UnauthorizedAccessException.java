package dev.be.coupon.domain.coupon.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class UnauthorizedAccessException extends CouponException {

    public UnauthorizedAccessException(final Object data) {
        super(ErrorType.UNAUTHORIZED_ACCESS, data);
    }
}
