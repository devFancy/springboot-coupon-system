package dev.be.coupon.api.config;


import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
@Component
public class SentryBeforeSendCallback implements SentryOptions.BeforeSendCallback {
    private static final String ERROR_CODE_TAG = "errorCode";
    private final Logger log = LoggerFactory.getLogger(SentryBeforeSendCallback.class);

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {

        if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
            SentryException sentryException = event.getExceptions().get(0);

            String exceptionType = sentryException.getType();
            String exceptionValue = sentryException.getValue();

            log.debug("Sentry Exception Type: {}, Value: {}", exceptionType, exceptionValue);
        }

        // hint 객체에서 원본 예외(Throwable)를 가져옵니다.
        Throwable throwable = event.getThrowable();

        // 발생한 예외가 우리가 정의한 CouponException 타입인지 확인합니다.
        if (throwable instanceof CouponException) {
            CouponException couponException = (CouponException) throwable;

            // CouponException에서 ErrorType을 가져옵니다.
            ErrorType errorType = couponException.getErrorType();
            // ErrorType과 그 안의 ErrorCode가 null이 아닌지 확인 후, ErrorCode의 이름(e.g., "E400", "E500")을 태그로 설정합니다.
            if (errorType != null && errorType.getCode() != null) {
                event.setTag(ERROR_CODE_TAG, errorType.getCode().name());
            }
        }
        return event;
    }
}


