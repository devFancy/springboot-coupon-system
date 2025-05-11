package dev.be.coupon.common.support.error;

public class CouponException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public CouponException(final ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = null;
    }

    public CouponException(final ErrorType errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Object getData() {
        return data;
    }
}
