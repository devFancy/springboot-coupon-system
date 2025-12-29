package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerRetryableException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.redisson.api.RRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueConsumer {

    private final CouponIssueService couponIssueService;
    private final RRateLimiter rateLimiter;
    private final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    public CouponIssueConsumer(final CouponIssueService couponIssueService,
                               final RRateLimiter rateLimiter) {
        this.couponIssueService = couponIssueService;
        this.rateLimiter = rateLimiter;
    }

    @KafkaListener(
            topics = "${kafka.topic.coupon-issue.name}",
            groupId = "coupon-issue-group"
    )
    public void listener(final CouponIssueMessage message,
                         final Acknowledgment ack) {
        rateLimiter.acquire(1);

        log.info("[CONSUMER_LISTENER] 메시지 처리를 시작합니다. userId={}, couponId={}", message.userId(), message.couponId());
        try {
            couponIssueService.issue(message.userId(), message.couponId());
            ack.acknowledge();
            log.info("[COUPON_ISSUE_SUCCESS] 쿠폰 발급이 완료되었습니다. userId={}", message.userId());
        } catch (CouponConsumerException e) {
            log.info("[COUPON_ISSUE_BUSINESS_ERROR] 비즈니스 오류로 인해 발급을 진행하지 않습니다. 사유: {}, userId={}", e.getMessage(), message.userId());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            log.warn("[COUPON_ISSUE_SKIP] 중복 발급이 감지되었습니다. 이미 발급된 건이므로 해당 처리를 스킵합니다. userId={}", message.userId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[COUPON_ISSUE_FAIL] 시스템 장애로 인해 쿠폰 발급이 실패했습니다. error={}", e.getMessage());
            throw new CouponConsumerRetryableException(ErrorType.COUPON_ISSUANCE_FAILED);
        }
    }

    // NOTE: 대규모 서비스와 같은 운영 환경에서는 별도의 재처리 전용 애플리케이션 서버를 실행해서 처리합니다.
    @KafkaListener(
            id = "dlq-consumer",
            topics = "${kafka.topic.coupon-issue.name}-dlq",
            groupId = "coupon-issue-dlq-group",
            autoStartup = "true"
    )
    public void listenDlq(final CouponIssueMessage message, final Acknowledgment ack) {
        log.info("[COUPON_ISSUE_DLQ_RETRY] 쿠폰 발급 실패 건에 대한 재처리를 시작합니다. userId={}", message.userId());
        try {
            couponIssueService.issue(message.userId(), message.couponId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[COUPON_ISSUE_DLQ_FAIL] 재처리 중 시스템 장애로 인해 쿠폰 발급이 실패했습니다. error={}", e.getMessage());
        }
    }
}
