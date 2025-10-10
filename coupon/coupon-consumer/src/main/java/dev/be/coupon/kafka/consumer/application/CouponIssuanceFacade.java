package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.application.exception.CouponNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssuanceFacade {

    private final CouponRepository couponRepository;
    private final CouponIssuanceService couponIssuanceService;
    private final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease;

    public CouponIssuanceFacade(final CouponRepository couponRepository,
                                final CouponIssuanceService couponIssuanceService,
                                final FailedCouponRetryCountIncrease failedCouponRetryCountIncrease) {
        this.couponRepository = couponRepository;
        this.couponIssuanceService = couponIssuanceService;
        this.failedCouponRetryCountIncrease = failedCouponRetryCountIncrease;
    }

    /**
     * 순서: Lock -> Transaction -> Unlock
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @DistributedLock(key = "'coupon:' + #message.couponId + ':' + #message.userId", waitTime = 5, leaseTime = 10)
    public void process(final CouponIssueMessage message) {
        try {
            validateCoupon(message.couponId());
            couponIssuanceService.issue(message.userId(), message.couponId());
        } catch (Exception e) {
            couponIssuanceService.recordFailure(message.userId(), message.couponId());
            throw new RuntimeException("쿠폰 발급 처리 도중 실패되었습니다. 실패된 쿠폰 발급 요청은 별도의 실패 테이블에 저장합니다.", e);
        }
    }

    @DistributedLock(key = "'coupon:' + #message.couponId + ':' + #message.userId", waitTime = 5, leaseTime = 10)
    public void processRetry(final CouponIssueMessage message) {
        try {
            validateCoupon(message.couponId());
            couponIssuanceService.reissue(message.userId(), message.couponId(), message.failedIssuedCouponId());
        } catch (Exception e) {
            failedCouponRetryCountIncrease.retryCountIncrease(message.failedIssuedCouponId());
            throw new RuntimeException("쿠폰 발급 요청에 대한 재처리가 실패되었습니다.", e);
        }
    }

    private void validateCoupon(final UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("..."));
        coupon.validateIssuableStatus(LocalDateTime.now());
    }
}
