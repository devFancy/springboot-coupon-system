package dev.be.coupon.api.support.error;

public class UserException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public UserException(final ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = null;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Object getData() {
        return data;
    }
}
