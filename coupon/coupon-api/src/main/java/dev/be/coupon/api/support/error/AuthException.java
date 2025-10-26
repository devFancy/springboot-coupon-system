package dev.be.coupon.api.support.error;

public class AuthException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public AuthException(final ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = null;
    }

    public AuthException(final ErrorType errorType, final Object data) {
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

