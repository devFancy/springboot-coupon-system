package dev.be.coupon.api.config;


import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Component;


/**
 * NOTE: Sentry로 전송되는 이벤트를 전처리하는 콜백이다.
 * - 중요도가 낮은(INFO) 비즈니스 예외는 Sentry로 전송하지 않는다.
 * - ErrorCode를 태그로 주입하여, 대시보드에서 정밀한 검색 및 알림 설정을 지원한다.
 */
@Component
public class SentryBeforeSendCallback implements SentryOptions.BeforeSendCallback {
    private static final String ERROR_CODE_TAG = "errorCode";
    private final Logger log = LoggerFactory.getLogger(SentryBeforeSendCallback.class);

    @Override
    public SentryEvent execute(@NotNull SentryEvent event, @NotNull Hint hint) {

        if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
            SentryException sentryException = event.getExceptions().get(0);
            log.debug("Sentry Exception Type: {}, Value: {}", sentryException.getType(), sentryException.getValue());
        }

        Throwable throwable = event.getThrowable();

        // 커스텀 예외(CouponException)인 경우에만 전처리 로직 수행
        if (throwable instanceof CouponException couponException) {
            ErrorType errorType = couponException.getErrorType();

            if (errorType != null) {
                if (errorType.getLogLevel() == LogLevel.INFO) {
                    return null;
                }
                if (errorType.getCode() != null) {
                    event.setTag(ERROR_CODE_TAG, errorType.getCode().name());
                }
            }
        }
        return event;
    }
}
