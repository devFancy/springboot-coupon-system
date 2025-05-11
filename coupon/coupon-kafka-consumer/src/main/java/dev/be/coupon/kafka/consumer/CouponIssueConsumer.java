package dev.be.coupon.kafka.consumer;

import dev.be.coupon.kafka.consumer.domain.FailedIssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.FailedIssuedCouponRepository;
import dev.be.coupon.kafka.consumer.domain.IssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import dev.be.coupon.kafka.consumer.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    public CouponIssueConsumer(final IssuedCouponRepository issuedCouponRepository,
                               final FailedIssuedCouponRepository failedIssuedCouponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
    }

    @KafkaListener(topics = "coupon_issue", groupId = "group_1")
    @Transactional
    public void listener(final CouponIssueMessage message) {
        log.info("발급 처리 메시지 수신: {}", message);
        try {
            IssuedCoupon issuedCoupon = new IssuedCoupon(message.userId(), message.couponId());
            issuedCouponRepository.save(issuedCoupon);
            log.info("쿠폰 발급 완료: {}", issuedCoupon);
        } catch (Exception e) {
            // Consumer 측 처리 실패 → 별도 이력 저장
            // 발급 실패 로그 기록 및 실패 이력 저장
            // 향후 배치 프로그램 또는 알림 시스템을 통해 실패 건 재처리 또는 관리자에게 알림 가능
            failedIssuedCouponRepository.save(new FailedIssuedCoupon(message.userId(), message.couponId()));
            log.error("Kafka Consumer 발급 실패 - userId: {}, couponId: {}", message.userId(), message.couponId(), e);
            // 예외를 던지면 Kafka가 자동 재시도 (설정에 따라)
            throw e;
        }
    }
}
