package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.infrastructure.kafka.producer.CouponIssueProducer;
import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * [발급 실패 건 재처리 스케줄러]
 *
 * - Kafka Consumer 에서 쿠폰 발급 처리 실패 시, FailedIssuedCoupon 엔티티에 저장
 * - 5분 간격으로 실패 건들을 재처리하여 Kafka Producer 를 통해 재전송 시도
 * - 재처리 성공 시 isResolved = true 처리, 실패 시 retryCount 증가
 * - findByIdWithLock()을 통해 동시에 여러 스케줄러 인스턴스가 동일 건을 처리하는 것을 방지
 *
 * [확장 고려 사항]
 * - 특정 횟수 이상 재시도 실패한 경우 (e.g., retryCount > 3) 해당 메시지를 Dead Letter Queue (DLQ) 토픽으로 이관하여
 *   추후 별도 장애 분석 및 관리자 수동 처리 대상에 포함할 수 있음
 * - DLQ 이관 시 KafkaProducer 를 통해 'dlq-coupon-issue' 등의 토픽으로 전송
 * - 재시도 성공률 및 DLQ 이관 건수는 Prometheus / Grafana 기반 모니터링 대상 지표로 확장 가능
 */
@Component
public class FailedCouponIssueRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FailedCouponIssueRetryScheduler.class);

    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final CouponIssueProducer couponIssueProducer;

    public FailedCouponIssueRetryScheduler(final FailedIssuedCouponRepository failedIssuedCouponRepository,
                                           final CouponIssueProducer couponIssueProducer) {
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
        this.couponIssueProducer = couponIssueProducer;
    }

    @Transactional
    @Scheduled(fixedDelay = 300_000)
    public void retryFailedCoupon() {
        List<FailedIssuedCoupon> failedIssuedCoupons = failedIssuedCouponRepository.findAllByIsResolvedFalse();

        if (failedIssuedCoupons.isEmpty()) {
            log.info("재시도할 쿠폰 발급 실패 건이 없습니다.");
            return;
        }

        log.info("재시도 대상 쿠폰 수: {}", failedIssuedCoupons.size());

        for (FailedIssuedCoupon failedIssuedCoupon : failedIssuedCoupons) {
            final UUID failedIssuedCouponId = failedIssuedCoupon.getId();

            try {
                FailedIssuedCoupon lockedCoupon = failedIssuedCouponRepository.findByIdWithLock(failedIssuedCouponId);
                couponIssueProducer.issue(failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId());
                lockedCoupon.markResolved();

                log.info("쿠폰 발급 재시도 성공 - userId={}, couponId={}", failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId());
            } catch (Exception e) {
                FailedIssuedCoupon lockedCoupon = failedIssuedCouponRepository.findByIdWithLock(failedIssuedCouponId); // 다시 락 획득
                lockedCoupon.increaseRetryCount();

                log.error("쿠폰 발급 재시도 실패 - userId={}, couponId={}, retryCount={}",
                        failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId(), failedIssuedCoupon.getRetryCount(), e);
            }
        }
    }
}
