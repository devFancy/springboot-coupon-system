package dev.be.coupon.infra.kafka.producer;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CouponIssueProducer
 * <p>
 * 쿠폰 발급 요청을 Kafka 토픽("coupon_issue")으로 비동기 전송합니다.
 * 이 메시지는 coupon-consumer 모듈의 CouponIssueConsumer 가 수신하여 DB에 저장합니다.
 */
@Component
public class CouponIssueProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String GLOBAL_TRACE_ID_HEADER = "globalTraceId";
    private static final Logger log = LoggerFactory.getLogger(CouponIssueProducer.class);

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

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("메시지 전송 성공. GlobalTraceId: {}, Topic: {}, Partition: {}, Offset: {}, Payload: {}",
                        globalTraceId,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        payload);
            } else {
                log.error("메시지 전송 실패 GlobalTraceId: {}, Payload: {}",
                        globalTraceId,
                        payload,
                        exception);
                // 여기에 실패 시 필요한 추가 로직을 구현 (e.g., 재시도, 알림, DB에 실패 기록 등)
            }
        });
    }
}
