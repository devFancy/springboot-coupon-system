package dev.be.coupon.api.common.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

public enum ErrorType {

    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.E500,
            "An unexpected error has occurred.",
            LogLevel.ERROR),

    INVALID_AUTH(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "인증 관련 부분에서 예외가 발생했습니다.",
            LogLevel.WARN),

    AUTHORIZATION_HEADER_MISSING(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.E401,
            "Header에 Authorization이 존재하지 않습니다.",
            LogLevel.WARN),

    INVALID_USER(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "사용자 관련 부분에서 예외가 발생했습니다.",
            LogLevel.WARN),

    INVALID_COUPON(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "쿠폰 관련 부분에서 예외가 발생했습니다.",
            LogLevel.WARN),

    INVALID_COUPON_TYPE(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "쿠폰 타입이 지정되지 않았습니다.",
            LogLevel.WARN),

    INVALID_COUPON_QUANTITY(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "쿠폰 발급 수량은 1 이상이야 합니다.",
            LogLevel.WARN),

    INVALID_COUPON_PERIOD(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "쿠폰 유효기간이 잘못되었습니다.",
            LogLevel.WARN),

    UNAUTHORIZED_ACCESS(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.E403,
            "권한이 없습니다.",
            LogLevel.WARN);

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
