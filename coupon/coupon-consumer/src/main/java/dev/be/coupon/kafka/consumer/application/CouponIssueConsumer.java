package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * "coupon_issue" Kafka 토픽으로부터 쿠폰 발급 메시지를 수신하여 발급 쿠폰을 DB에 저장합니다.
 */
@Component
public class CouponIssueConsumer {

    private final CouponIssuanceService couponIssuanceService;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);
    private static final String GLOBAL_TRACE_ID_KEY = "globalTraceId";

    public CouponIssueConsumer(final CouponIssuanceService couponIssuanceService) {
        this.couponIssuanceService = couponIssuanceService;
    }

    /**
     * Kafka 토픽으로부터 메시지를 수신하여 쿠폰을 발급합니다.
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @KafkaListener(topics = "#{T(dev.be.coupon.infra.kafka.KafkaTopic).COUPON_ISSUE.getTopicName()}", groupId = "group_1")
    public void listener(final CouponIssueMessage message,
                         @Header(name = GLOBAL_TRACE_ID_KEY, required = false) final String globalTraceId,
                         final Acknowledgment ack) {

        if (!Objects.isNull(globalTraceId)) {
            MDC.put(GLOBAL_TRACE_ID_KEY, globalTraceId);
        }
        log.info("발급 처리 메시지 수신: {}", message);

        try {
            couponIssuanceService.process(message);
        } finally {
            ack.acknowledge();
            MDC.remove(GLOBAL_TRACE_ID_KEY);
        }
    }
}
