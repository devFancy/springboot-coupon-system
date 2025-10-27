package dev.be.coupon.api.coupon.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class FailedCouponIssueRetryScheduler {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 100;

    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponRetryFailureHandler couponRetryFailureHandler;
    private static final Logger log = LoggerFactory.getLogger(FailedCouponIssueRetryScheduler.class);

    public FailedCouponIssueRetryScheduler(final FailedIssuedCouponRepository failedIssuedCouponRepository,
                                           final CouponIssueProducer couponIssueProducer,
                                           final CouponRetryFailureHandler couponRetryFailureHandler) {
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.couponRetryFailureHandler = couponRetryFailureHandler;
    }

    /**
     * Note: 주기적으로 실행되어 쿠폰 발급 실패 건을 재처리합니다.
     */
    @Scheduled(fixedDelay = 300_000) // 5분
    public void retryFailedCoupon() {
        final Pageable batchSize = PageRequest.of(0, BATCH_SIZE);

        Slice<FailedIssuedCoupon> failedIssuedCoupons = failedIssuedCouponRepository
                .findAllByIsResolvedFalseAndRetryCountLessThan(MAX_RETRY_COUNT, batchSize);

        if (failedIssuedCoupons.isEmpty()) {
            log.info("재시도할 쿠폰 발급 실패 건이 없습니다.");
            return;
        }

        log.info("재시도 대상 쿠폰 수: (이번 배치): {}", failedIssuedCoupons.getNumberOfElements());

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
