package dev.be.coupon.infra.kafka.producer;

import dev.be.coupon.infra.kafka.KafkaTopic;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 쿠폰 발급 요청을 Kafka 토픽으로 전송합니다.
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

    /**
     * 쿠폰 발급 요청 메시지를 동기적으로 Kafka에 발행합니다.
     * .join()을 호출하여 메시지 전송이 완료될 때까지 현재 스레드를 블로킹합니다.
     */
    public void issue(final UUID userId, final UUID couponId) {
        CouponIssueMessage payload = new CouponIssueMessage(userId, couponId);
        String globalTraceId = MDC.get("globalTraceId");

        ProducerRecord<String, Object> record = new ProducerRecord<>(KafkaTopic.COUPON_ISSUE.getTopicName(), payload);

        if (globalTraceId != null) {
            record.headers().add(GLOBAL_TRACE_ID_HEADER, globalTraceId.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("메시지 전송 실패. GlobalTraceId: {}, Record: {}",
                        globalTraceId, record, ex);
            } else {
                log.info("메시지 전송 성공. GlobalTraceId: {}, Topic: {}, Partition: {}, Offset: {}, Payload: {}",
                        globalTraceId,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        payload);
            }
        }).join(); // 이 메서드가 호출되면, whenComplete의 콜백이 실행될 때까지 스레드가 블로킹됩니다.
    }
}
