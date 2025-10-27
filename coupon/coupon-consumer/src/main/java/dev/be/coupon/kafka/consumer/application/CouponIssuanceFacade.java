package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssuanceFacade {

    private final CouponRepository couponRepository;
    private final CouponIssuanceService couponIssuanceService;

    public CouponIssuanceFacade(final CouponRepository couponRepository,
                                final CouponIssuanceService couponIssuanceService) {
        this.couponRepository = couponRepository;
        this.couponIssuanceService = couponIssuanceService;
    }

    /**
     * 순서: Lock -> Transaction -> Unlock
     */
    @DistributedLock(key = "'coupon:' + #message.couponId + ':' + #message.userId", waitTime = 5, leaseTime = 10)
    public void process(final CouponIssueMessage message) {
        validateCoupon(message.couponId());
        couponIssuanceService.issue(message.userId(), message.couponId());
    }

    @DistributedLock(key = "'coupon:' + #message.couponId + ':' + #message.userId", waitTime = 5, leaseTime = 10)
    public void processRetry(final CouponIssueMessage message) {
        validateCoupon(message.couponId());
        couponIssuanceService.reissue(message.userId(), message.couponId(), message.failedIssuedCouponId());
    }

    private void validateCoupon(final UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponConsumerException(ErrorType.COUPON_NOT_FOUND));
        coupon.validateStatusIsActive(LocalDateTime.now());
    }
}
