package dev.be.coupon.domain.user.exception;



public class UserDomainException extends RuntimeException {
    public UserDomainException(final String message) {
        super(message);
    }
}
