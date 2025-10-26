package dev.be.coupon.kafka.consumer.support.error;

public class CouponConsumerException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public CouponConsumerException(final ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = null;
    }

    public CouponConsumerException(final ErrorType errorType, final Object data) {
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
