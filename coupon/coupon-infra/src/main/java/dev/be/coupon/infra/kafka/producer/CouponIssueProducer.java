package dev.be.coupon.infra.kafka.producer;

import dev.be.coupon.infra.exception.CouponInfraException;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
public class CouponIssueProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String issueTopic;
    private static final Logger log = LoggerFactory.getLogger(CouponIssueProducer.class);

    public CouponIssueProducer(final KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${kafka.topic.coupon-issue}") final String issueTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.issueTopic = issueTopic;
    }


    public void issue(final UUID userId, final UUID couponId) {
        CouponIssueMessage payload = new CouponIssueMessage(userId, couponId);
        ProducerRecord<String, Object> record = new ProducerRecord<>(issueTopic, userId.toString(), payload);
        try {
            sendAndWaitForCompletion(record, payload);
        } catch (Exception e) {
            log.error("[KAFKA_SEND_FAIL] 메시지 발행 실패. Record: {} errorMessage={}", record, e.getMessage(), e);
            throw new CouponInfraException("Kafka 메시지 발행이 실패했습니다.");
        }
    }

    private void sendAndWaitForCompletion(final ProducerRecord<String, Object> record, final CouponIssueMessage payload) {
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("메시지 전송 실패. Record: {}", record, ex);
            } else {
                log.info("메시지 전송 성공. Topic: {}, Partition: {}, Offset: {}, Payload: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        payload);
            }
        }).join();
    }
}
