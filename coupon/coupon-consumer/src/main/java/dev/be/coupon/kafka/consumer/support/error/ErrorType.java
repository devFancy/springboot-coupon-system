package dev.be.coupon.kafka.consumer.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "예상치 못한 에러가 발생했습니다.", LogLevel.ERROR),

    // Coupon(쿠폰)
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E3000, "존재하지 않는 쿠폰입니다.", LogLevel.INFO),
    COUPON_ALREADY_ISSUED(HttpStatus.BAD_REQUEST, ErrorCode.E3001, "이미 발급된 쿠폰입니다.", LogLevel.INFO),
    COUPON_STATUS_IS_NOT_ACTIVE(HttpStatus.BAD_REQUEST, ErrorCode.E3002, "쿠폰이 현재 사용 가능한 상태가 아닙니다.", LogLevel.INFO),
    COUPON_ISSUANCE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.E3003, "시스템 장애로 인해 발급이 실패되었습니다.", LogLevel.ERROR);

    private final HttpStatus status;
    private final ErrorCode code;
    private final String message;
    private final LogLevel logLevel;

    ErrorType(final HttpStatus status, final ErrorCode code, final String message, final LogLevel logLevel) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.logLevel = logLevel;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }
}
