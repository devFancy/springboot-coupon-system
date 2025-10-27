package dev.be.coupon.kafka.consumer.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "예상치 못한 에러가 발생했습니다.", LogLevel.ERROR),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청이 올바르지 않습니다.", LogLevel.INFO),
    NOT_FOUND_DATA(HttpStatus.BAD_REQUEST, ErrorCode.E401, "해당 데이터를 찾을 수 없습니다.", LogLevel.ERROR),

    // Coupon(쿠폰)
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E3000, "존재하지 않는 쿠폰입니다.", LogLevel.INFO),
    COUPON_ISSUANCE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E3001, "쿠폰 발급 처리 중 DB 장애로 실패 기록조차 실패. Kafka 재시도 필요", LogLevel.ERROR),
    COUPON_ISSUANCE_RETRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E3002, "쿠폰 발급 처리 중 DB 장애로 재처리 실패. Kafka 재시도 필요", LogLevel.ERROR);

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
