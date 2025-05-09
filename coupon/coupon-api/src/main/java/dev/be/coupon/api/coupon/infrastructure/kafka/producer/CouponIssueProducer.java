package dev.be.coupon.api.coupon.infrastructure.kafka.producer;

import dev.be.coupon.api.coupon.infrastructure.kafka.dto.CouponIssueMessage;
import org.springframework.kafka.core.KafkaTemplate;
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

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    public CouponIssueProducer(final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void issue(final UUID userId, final UUID couponId) {
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);
        kafkaTemplate.send("coupon_issue", message);
    }
}
