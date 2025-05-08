package dev.be.coupon.kafka.consumer;

import dev.be.coupon.kafka.consumer.domain.IssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import dev.be.coupon.kafka.dto.CouponIssueMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
