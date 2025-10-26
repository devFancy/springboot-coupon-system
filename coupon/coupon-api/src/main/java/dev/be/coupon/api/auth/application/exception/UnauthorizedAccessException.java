package dev.be.coupon.api.auth.application.exception;


import dev.be.coupon.api.support.error.AuthException;
import dev.be.coupon.api.support.error.ErrorType;

public class UnauthorizedAccessException extends AuthException {

    public UnauthorizedAccessException(final Object data) {
        super(ErrorType.AUTH_ACCESS_DENIED, data);
    }
}
