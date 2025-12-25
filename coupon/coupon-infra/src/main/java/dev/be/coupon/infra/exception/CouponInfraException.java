package dev.be.coupon.infra.exception;

public class CouponInfraException extends RuntimeException {
    public CouponInfraException(final String message) {
        super(message);
    }
}
