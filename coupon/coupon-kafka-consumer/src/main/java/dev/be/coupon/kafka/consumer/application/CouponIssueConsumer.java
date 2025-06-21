package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.kafka.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * CouponIssueConsumer
 * <p>
 * "coupon_issue" Kafka 토픽으로부터 쿠폰 발급 메시지를 수신하여
 * 발급 쿠폰을 DB에 저장합니다.
 * <p>
 * 수신된 메시지는 CouponIssueMessage(userId, couponId) 형태이며,
 * 실제 저장 로직은 IssuedCouponRepository 를 통해 수행됩니다.
 */
@Component
public class CouponIssueConsumer {

    private final CouponIssueService couponIssueService;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);
    private static final String GLOBAL_TRACE_ID_KEY = "globalTraceId";

    public CouponIssueConsumer(final CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    /**
     * Kafka "coupon_issue" 토픽으로부터 메시지를 수신하여 쿠폰을 발급합니다.
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @KafkaListener(topics = "coupon_issue", groupId = "group_1")
    public void listener(final CouponIssueMessage message, @Header(name = GLOBAL_TRACE_ID_KEY, required = false) String globalTraceId, final Acknowledgment ack) {
        if (!Objects.isNull(globalTraceId)) {
            MDC.put(GLOBAL_TRACE_ID_KEY, globalTraceId);
        }
        log.info("발급 처리 메시지 수신: {}", message);

        try {
            // 모든 비즈니스 로직을 Service 에 위임합니다.
            couponIssueService.issue(message);
        } finally {
            ack.acknowledge();
            MDC.remove(GLOBAL_TRACE_ID_KEY);
        }
    }
}
