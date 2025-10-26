package dev.be.coupon.kafka.consumer.support.handler;

import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import dev.be.coupon.kafka.consumer.support.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(CouponConsumerException.class)
    public ResponseEntity<ApiResponse<?>> handleCouponConsumerException(CouponConsumerException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CouponConsumerException: {}", e.getMessage(), e);
            case WARN -> log.warn("CouponConsumerException: {}", e.getMessage(), e);
            default -> log.info("CouponConsumerException: {}", e.getMessage(), e);
        }
        return new ResponseEntity<>(ApiResponse.error(e.getErrorType(), e.getData()), e.getErrorType().getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Exception : {}", e.getMessage(), e);
        return new ResponseEntity<>(ApiResponse.error(ErrorType.DEFAULT_ERROR), ErrorType.DEFAULT_ERROR.getStatus());
    }
}
