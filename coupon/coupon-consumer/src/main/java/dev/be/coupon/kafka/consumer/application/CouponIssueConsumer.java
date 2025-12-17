package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.redisson.api.RRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;


@Component
public class CouponIssueConsumer {

    private final CouponIssuanceFacade couponIssuanceFacade;
    private final CouponIssueFailureHandler couponIssueFailureHandler;
    private final RRateLimiter rateLimiter;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    public CouponIssueConsumer(final CouponIssuanceFacade couponIssuanceFacade,
                               final CouponIssueFailureHandler couponIssueFailureHandler,
                               final RRateLimiter rateLimiter) {
        this.couponIssuanceFacade = couponIssuanceFacade;
        this.couponIssueFailureHandler = couponIssueFailureHandler;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Kafka 토픽으로부터 메시지를 수신하여 쿠폰을 발급합니다.
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @KafkaListener(topics = "${kafka.topic.coupon-issue}", groupId = "group_1")
    public void listener(final CouponIssueMessage message,
                         final Acknowledgment ack) {
        // NOTE: 설정된 TPS를 초과하면 다음 토큰이 생길 때까지 스레드가 대기합니다.
        rateLimiter.acquire(1);

        log.info("[CouponIssueConsumer_listener] 발급 처리 메시지 수신: userId={}, couponId={}", message.userId(), message.couponId());
        try {
            couponIssuanceFacade.process(message);
            ack.acknowledge();
        } catch (Exception e) {
            couponIssueFailureHandler.handle(message, ack, e);
        }
    }

    @KafkaListener(topics = "${kafka.topic.coupon-issue-retry}", groupId = "group_1")
    public void listenerRetry(final CouponIssueMessage message,
                              final Acknowledgment ack) {
        rateLimiter.acquire(1);

        log.info("[CouponIssueConsumer_listenerRetry] 재시도 발급 처리 메시지 수신: userId={}, couponId={}", message.userId(), message.couponId());
        try {
            couponIssuanceFacade.processRetry(message);
            ack.acknowledge();
        } catch (Exception e) {
            couponIssueFailureHandler.handleRetry(message, ack, e);
        }
    }
}
