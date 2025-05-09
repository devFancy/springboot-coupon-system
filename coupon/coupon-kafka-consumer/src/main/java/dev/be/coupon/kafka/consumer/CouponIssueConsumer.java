package dev.be.coupon.kafka.consumer;

import dev.be.coupon.kafka.consumer.domain.IssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import dev.be.coupon.kafka.dto.CouponIssueMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final IssuedCouponRepository issuedCouponRepository;

    public CouponIssueConsumer(final IssuedCouponRepository issuedCouponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
    }

    @KafkaListener(topics = "coupon_issue", groupId = "group_1")
    @Transactional
    public void listener(final CouponIssueMessage message) {
        System.out.println("발급 처리 메시지 수신: " + message);

        IssuedCoupon issuedCoupon = new IssuedCoupon(message.userId(), message.couponId());
        issuedCouponRepository.save(issuedCoupon);

        System.out.println("쿠폰 발급 완료: " + issuedCoupon);
    }
}
