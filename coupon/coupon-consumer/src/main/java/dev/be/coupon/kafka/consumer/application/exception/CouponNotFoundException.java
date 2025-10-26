package dev.be.coupon.kafka.consumer.application.exception;


import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;

public class CouponNotFoundException extends CouponConsumerException {

    public CouponNotFoundException(final Object data) {
        super(ErrorType.COUPON_NOT_FOUND, data);
    }
}
