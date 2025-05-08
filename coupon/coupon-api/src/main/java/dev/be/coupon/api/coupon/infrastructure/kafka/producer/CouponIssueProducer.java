package dev.be.coupon.api.coupon.infrastructure.kafka.producer;

import dev.be.coupon.api.coupon.infrastructure.kafka.dto.CouponIssueMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
