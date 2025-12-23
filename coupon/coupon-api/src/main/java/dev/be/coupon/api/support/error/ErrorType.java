package dev.be.coupon.api.support.error;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

public enum ErrorType {

    // 공통
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "서버에서 예상치 못한 에러가 발생했습니다.", LogLevel.ERROR),
    SENTRY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "Sentry 테스트를 위한 강제 예외 발생!", LogLevel.ERROR),

    // Auth(인증)
    AUTH_HEADER_MISSING(HttpStatus.UNAUTHORIZED, ErrorCode.E1000, "Header에 Authorization이 존재하지 않습니다.", LogLevel.INFO),
    AUTH_ACCESS_DENIED(HttpStatus.UNAUTHORIZED, ErrorCode.E1001, "권한이 없습니다.", LogLevel.INFO),
    AUTH_ADMIN_ONLY(HttpStatus.FORBIDDEN, ErrorCode.E1002, "해당 리소스에 대한 관리자 권한이 필요합니다.", LogLevel.WARN),
    AUTH_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, ErrorCode.E1003, "토큰이 만료되었습니다.", LogLevel.INFO),
    AUTH_INVALID_TOKEN(HttpStatus.BAD_REQUEST, ErrorCode.E1004, "인증 관련 부분에서 예외가 발생했습니다.", LogLevel.INFO),

    // User(사용자)
    USER_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, ErrorCode.E2000, "이미 존재하는 이름입니다.", LogLevel.INFO),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E2001, "존재하지 않는 사용자입니다.", LogLevel.INFO),
    USER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCode.E2002, "비밀번호가 일치하지 않습니다.", LogLevel.INFO),

    // Coupon(쿠폰)
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E3000, "존재하지 않는 쿠폰입니다.", LogLevel.INFO),
    ISSUED_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E3001, "발급되지 않았거나 소유하지 않은 쿠폰입니다.", LogLevel.INFO),
    COUPON_NOT_ACTIVE(HttpStatus.NOT_FOUND, ErrorCode.E3002, "발급되지 않았거나 소유하지 않은 쿠폰입니다.", LogLevel.INFO),
    ;

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
