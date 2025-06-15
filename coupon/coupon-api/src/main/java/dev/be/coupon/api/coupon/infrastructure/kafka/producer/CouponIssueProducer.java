package dev.be.coupon.api.coupon.infrastructure.kafka.producer;

import dev.be.coupon.api.coupon.infrastructure.kafka.dto.CouponIssueMessage;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * CouponIssueProducer
 * <p>
 * 쿠폰 발급 요청을 Kafka 토픽("coupon_issue")으로 비동기 전송합니다.
 * 이 메시지는 coupon-kafka-consumer 모듈의 CouponIssueConsumer 가 수신하여 DB에 저장합니다.
 */
@Component
public class CouponIssueProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String GLOBAL_TRACE_ID_HEADER = "globalTraceId";

    public CouponIssueProducer(final KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void issue(final UUID userId, final UUID couponId) {
        CouponIssueMessage payload = new CouponIssueMessage(userId, couponId);
        String globalTraceId = MDC.get("globalTraceId");

        Message<CouponIssueMessage> message = MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, "coupon_issue")
                .setHeader(GLOBAL_TRACE_ID_HEADER, globalTraceId)
                .build();

        kafkaTemplate.send(message);
    }
}
