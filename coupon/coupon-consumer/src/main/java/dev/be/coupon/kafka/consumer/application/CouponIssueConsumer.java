package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;


@Component
public class CouponIssueConsumer {

    private final CouponIssuanceFacade couponIssuanceFacade;
    private final CouponIssuanceService couponIssuanceService;
    private final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    public CouponIssueConsumer(final CouponIssuanceFacade couponIssuanceFacade,
                               final CouponIssuanceService couponIssuanceService,
                               final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease) {
        this.couponIssuanceFacade = couponIssuanceFacade;
        this.couponIssuanceService = couponIssuanceService;
        this.failedCouponRetryCountIncrease = failedCouponRetryCountIncrease;
    }

    /**
     * Kafka 토픽으로부터 메시지를 수신하여 쿠폰을 발급합니다.
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @KafkaListener(topics = "${kafka.topic.coupon-issue}", groupId = "group_1")
    public void listener(final CouponIssueMessage message,
                         final Acknowledgment ack) {
        log.info("발급 처리 메시지 수신: {}", message);

        try {
            couponIssuanceFacade.process(message);
            ack.acknowledge();
        } catch (CouponDomainException | CouponConsumerException e) {
            log.info("[CouponIssueConsumer_listener] 비즈니스 예외 발생. 메시지 폐기. ack: {} (메시지: {})", e.getMessage(), message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[CouponIssueConsumer_listener] 인프라 예외 발생. 실패 이력 저장 시도...: {} (메시지: {})", e.getMessage(), message, e);
            try {
                couponIssuanceService.recordFailure(message.userId(), message.couponId());
                ack.acknowledge();
            } catch (Exception dbFail) {
                log.error("[CouponIssueConsumer_listener] DB 완전 장애! 실패 기록조차 실패. nack()로 Kafka 재시도 유도", dbFail);
                throw new CouponConsumerException(ErrorType.COUPON_ISSUANCE_FAILED);
            }
        }
    }

    @KafkaListener(topics = "${kafka.topic.coupon-issue-retry}", groupId = "group_1")
    public void listenerRetry(final CouponIssueMessage message,
                         final Acknowledgment ack) {
        log.info("발급 처리 메시지 수신: {}", message);

        try {
            couponIssuanceFacade.processRetry(message);
            ack.acknowledge();
        } catch (CouponDomainException | CouponConsumerException e) {
            log.warn("[CouponIssueConsumer_listenerRetry] 재시도 중 비즈니스 예외 발생. 재시도 횟수 증가 후 포기. ack: {} (메시지: {})", e.getMessage(), message);
            failedCouponRetryCountIncrease.retryCountIncrease(message.failedIssuedCouponId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[CouponIssueConsumer_listenerRetry] 재시도 중 인프라 예외 발생. 재시도 횟수 증가 시도...: {} (메시지: {})", e.getMessage(), message, e);
            try {
                failedCouponRetryCountIncrease.retryCountIncrease(message.failedIssuedCouponId());
                ack.acknowledge();
            } catch (Exception dbFail) {
                log.error("[CouponIssueConsumer_listenerRetry] DB 완전 장애! 횟수 증가조차 실패. nack()로 Kafka 재시도 유도", dbFail);
                throw new CouponConsumerException(ErrorType.COUPON_ISSUANCE_RETRY_FAILED);
            }
        }
    }
}
