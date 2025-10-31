package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueFailureHandler {

    private final CouponIssuanceService couponIssuanceService;
    private final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease;

    private final Logger log = LoggerFactory.getLogger(CouponIssueFailureHandler.class);

    public CouponIssueFailureHandler(final CouponIssuanceService couponIssuanceService,
                                     final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease) {
        this.couponIssuanceService = couponIssuanceService;
        this.failedCouponRetryCountIncrease = failedCouponRetryCountIncrease;
    }


    public void handle(final CouponIssueMessage message, final Acknowledgment ack, final Exception e) {
        if (isBusinessException(e)) {
            log.info("[CouponIssueFailureHandler_handle] 비즈니스 예외 발생. userId={}, couponId={}, error: {}",
                    message.userId(), message.couponId(), e.getMessage());
            ack.acknowledge();
        } else {
            log.error("[CouponIssueFailureHandler_handle] 인프라 예외 발생. userId={}, couponId={}, error: {}",
                    message.userId(), message.couponId(), e.getMessage(), e);
            try {
                couponIssuanceService.recordFailure(message.userId(), message.couponId());
                ack.acknowledge();
            } catch (Exception dbFail) {
                log.error("[CouponIssueFailureHandler_handle] DB 다운! 실패 기록조차 실패. Kafka 재시도 유도. error={}", dbFail.getMessage(), dbFail);
                throw new CouponConsumerException(ErrorType.COUPON_ISSUANCE_FAILED);
            }
        }
    }


    public void handleRetry(final CouponIssueMessage message, final Acknowledgment ack, final Exception e) {
        if (isBusinessException(e)) {
            log.info("[CouponIssueFailureHandler_handleRetry] 재시도 중 비즈니스 예외 발생. 재시도 횟수 증가 후 포기. userId={}, couponId={}, error: {}",
                    message.userId(), message.couponId(), e.getMessage());
            failedCouponRetryCountIncrease.retryCountIncrease(message.failedIssuedCouponId());
            ack.acknowledge();
        } else {
            log.error("[CouponIssueFailureHandler_handleRetry] 재시도 중 인프라 예외 발생. 재시도 횟수 증가 시도. userId={}, couponId={}, error: {}",
                    message.userId(), message.couponId(), e.getMessage(), e);
            try {
                failedCouponRetryCountIncrease.retryCountIncrease(message.failedIssuedCouponId());
                ack.acknowledge();
            } catch (Exception dbFail) {
                log.error("[CouponIssueFailureHandler_handleRetry] DB 다운! 횟수 증가조차 실패. nack()로 Kafka 재시도 유도. error={}", dbFail.getMessage(), dbFail);
                throw new CouponConsumerException(ErrorType.COUPON_ISSUANCE_RETRY_FAILED);
            }
        }
    }

    private boolean isBusinessException(final Exception e) {
        return e instanceof CouponDomainException || e instanceof CouponConsumerException;
    }
}
