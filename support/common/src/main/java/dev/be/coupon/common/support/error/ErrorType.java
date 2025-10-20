package dev.be.coupon.common.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

public enum ErrorType {

    SENTRY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.E500,
            "Sentry 테스트를 위한 강제 예외 발생!",
            LogLevel.ERROR),

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
    INVALID_ISSUED_COUPON(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "발급된 쿠폰 관련 부분에서 예외가 발생했습니다.",
            LogLevel.WARN),

    INVALID_COUPON_STATUS(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "쿠폰 상태가 활성 상태가 아닙니다.",
            LogLevel.WARN),

    INVALID_COUPON_ISSUABLE(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "현재 쿠폰은 발급 가능한 상태가 아닙니다.",
            LogLevel.WARN),

    INVALID_COUPON_USAGE_STATUS(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "현재 쿠폰은 사용 가능한 상태가 아닙니다.",
            LogLevel.WARN),

    INVALID_COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST,
            ErrorCode.E400,
            "이미 사용된 쿠폰입니다.",
            LogLevel.WARN),

    NOTFOUND_COUPON(HttpStatus.NOT_FOUND,
            ErrorCode.E404,
            "존재하지 않는 쿠폰입니다.",
            LogLevel.WARN),

    NOTFOUND_ISSUED_COUPON(HttpStatus.NOT_FOUND,
            ErrorCode.E404,
            "발급되지 않았거나 소유하지 않은 쿠폰입니다.",
            LogLevel.WARN),

    UNAUTHORIZED_ACCESS(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.E401,
            "권한이 없습니다.",
            LogLevel.WARN),

    DISTRIBUTED_LOCK_NOT_ACQUIRED(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.E503,
            "일시적으로 요청이 많아 락을 획득할 수 없습니다. 잠시 후 다시 시도해주세요.",
            LogLevel.WARN
    ),

    COUPON_ISSUE_PROCESSING_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.E500,
            "쿠폰 발급 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
            LogLevel.ERROR
    );

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
