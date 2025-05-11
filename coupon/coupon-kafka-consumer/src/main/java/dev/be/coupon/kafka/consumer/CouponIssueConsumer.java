package dev.be.coupon.kafka.consumer;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.kafka.consumer.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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

    /**
     * Kafka "coupon_issue" 토픽으로부터 메시지를 수신하여 쿠폰을 발급합니다.
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @KafkaListener(topics = "coupon_issue", groupId = "group_1")
    @Transactional
    public void listener(final CouponIssueMessage message, final Acknowledgment ack) {
        log.info("발급 처리 메시지 수신: {}", message);
        try {
            IssuedCoupon issuedCoupon = new IssuedCoupon(message.userId(), message.couponId());
            issuedCouponRepository.save(issuedCoupon);
            log.info("쿠폰 발급 완료: {}", issuedCoupon);
            ack.acknowledge();
        } catch (Exception e) {
            // 실패 이력 저장 (향후 스케줄러에서 재처리)
            failedIssuedCouponRepository.save(new FailedIssuedCoupon(message.userId(), message.couponId()));
            log.error("쿠폰 발급 실패 - userId: {}, couponId: {}, reason={}", message.userId(), message.couponId(), e.getMessage(), e);
            throw e;
        }
    }
}
