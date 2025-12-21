package dev.be.coupon.api.support.handler;

import dev.be.coupon.api.support.error.AuthException;
import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import dev.be.coupon.api.support.error.UserException;
import dev.be.coupon.api.support.response.ApiResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResultResponse<?>> handleAuthException(final AuthException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("AuthException: {}", e.getMessage(), e);
            case WARN -> log.warn("AuthException: {}", e.getMessage(), e);
            default -> log.info("AuthException: {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(ApiResultResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResultResponse<?>> handleUserException(final UserException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("UserException: {}", e.getMessage(), e);
            case WARN -> log.warn("UserException: {}", e.getMessage(), e);
            default -> log.info("UserException: {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(ApiResultResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(CouponException.class)
    public ResponseEntity<ApiResultResponse<?>> handleCouponApiException(final CouponException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CouponException: {}", e.getMessage(), e);
            case WARN -> log.warn("CouponException: {}", e.getMessage(), e);
            default -> log.info("CouponException: {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(ApiResultResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResultResponse<?>> handleException(final Exception e) {
        log.error("Exception : {}", e.getMessage(), e);
        return new ResponseEntity<>(ApiResultResponse.error(ErrorType.DEFAULT_ERROR), ErrorType.DEFAULT_ERROR.getStatus());
    }
}
