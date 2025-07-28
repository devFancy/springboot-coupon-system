package dev.be.coupon.kafka.consumer.application.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class CouponNotFoundException extends CouponException {

    public CouponNotFoundException(final Object data) {
        super(ErrorType.NOTFOUND_COUPON, data);
    }
}
