package dev.be.coupon.infra.kafka.producer;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 쿠폰 발급 요청을 Kafka 토픽으로 전송합니다.
 * 이 메시지는 coupon-consumer 모듈의 CouponIssueConsumer 가 수신하여 DB에 저장합니다.
 */
@Component
public class CouponIssueProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String issueTopic;
    private final String retryTopic;
    private static final Logger log = LoggerFactory.getLogger(CouponIssueProducer.class);

    public CouponIssueProducer(final KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${kafka.topic.coupon-issue}") final String issueTopic,
                               @Value("${kafka.topic.coupon-issue-retry}") final String retryTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.issueTopic = issueTopic;
        this.retryTopic = retryTopic;
    }

    /**
     * NOTE: 쿠폰 발급 요청 메시지를 동기적으로 Kafka에 발행합니다. (일반 토픽)
     * - key: userId를 사용하여 같은 사용자 요청은 항상 같은 파티션에 전송되도록 순서를 보장합니다.
     */
    public void issue(final UUID userId, final UUID couponId) {
        CouponIssueMessage payload = new CouponIssueMessage(userId, couponId, null);
        ProducerRecord<String, Object> record = new ProducerRecord<>(issueTopic, userId.toString(), payload);

        sendAndWaitForCompletion(record, payload);
    }

    /**
     * NOTE: 쿠폰 발급 요청 메시지를 동기적으로 Kafka에 발행합니다. (재시도 토픽)
     * - key: userId를 사용하여 같은 사용자 요청은 항상 같은 파티션에 전송되도록 순서를 보장합니다.
     */
    public void issueRetry(final UUID userId, final UUID couponId, final UUID failedIssuedCouponId) {
        CouponIssueMessage payload = new CouponIssueMessage(userId, couponId, failedIssuedCouponId);
        ProducerRecord<String, Object> retryRecord = new ProducerRecord<>(retryTopic, userId.toString(), payload);

        sendAndWaitForCompletion(retryRecord, payload);
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
        }).join(); // 이 메서드가 호출되면, whenComplete 의 콜백이 실행될 때까지 스레드가 블로킹됩니다.
    }
}
