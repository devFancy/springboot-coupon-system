package dev.be.coupon.api.exceptionhanlder;

import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import dev.be.coupon.api.support.response.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(CouponException.class)
    public ResponseEntity<CommonResponse<?>> handleCouponException(CouponException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CouponException: {}", e.getMessage(), e);
            case WARN -> log.warn("CouponException: {}", e.getMessage(), e);
            default -> log.info("CouponException: {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(CommonResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<?>> handleException(Exception e) {
        log.error("Exception : {}", e.getMessage(), e);
        return new ResponseEntity<>(CommonResponse.error(ErrorType.DEFAULT_ERROR), ErrorType.DEFAULT_ERROR.getStatus());
    }
}
