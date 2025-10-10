package dev.be.coupon.api.coupon.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [발급 실패 건 재처리]
 * Kafka Consumer에서 처리 실패한 쿠폰 발급 건을 재시도합니다.
 * 이 방식은 Dead Letter Queue (DLQ) 패턴으로 대체할 수 있습니다.
 */
@Component
public class FailedCouponIssueRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FailedCouponIssueRetryScheduler.class);
    private static final int MAX_RETRY_COUNT = 5;

    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponRetryFailureHandler couponRetryFailureHandler;

    public FailedCouponIssueRetryScheduler(final FailedIssuedCouponRepository failedIssuedCouponRepository,
                                           final CouponIssueProducer couponIssueProducer,
                                           final CouponRetryFailureHandler couponRetryFailureHandler) {
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.couponRetryFailureHandler = couponRetryFailureHandler;
    }

    @Scheduled(fixedDelay = 300_000)
    public void retryFailedCoupon() {
        List<FailedIssuedCoupon> failedIssuedCoupons = failedIssuedCouponRepository
                .findAllByIsResolvedFalseAndRetryCountLessThan(MAX_RETRY_COUNT);

        if (failedIssuedCoupons.isEmpty()) {
            log.info("재시도할 쿠폰 발급 실패 건이 없습니다.");
            return;
        }

        log.info("재시도 대상 쿠폰 수: {}", failedIssuedCoupons.size());

        for (FailedIssuedCoupon failedIssuedCoupon : failedIssuedCoupons) {
            try {
                couponIssueProducer.issueRetry(failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId(), failedIssuedCoupon.getId());
                log.info("쿠폰 발급 재시도 성공 - userId={}, couponId={}", failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId());
            } catch (Exception e) {
                log.error("쿠폰 발급 재시도 실패 - userId={}, couponId={}, retryCount={}",
                        failedIssuedCoupon.getUserId(), failedIssuedCoupon.getCouponId(), failedIssuedCoupon.getRetryCount(), e);
                couponRetryFailureHandler.handleFailure(failedIssuedCoupon.getId());
            }
        }
    }
}
