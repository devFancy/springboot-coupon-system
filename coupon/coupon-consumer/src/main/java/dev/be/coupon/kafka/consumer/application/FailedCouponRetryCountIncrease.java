package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FailedCouponRetryCountIncrease {

    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final Logger log = LoggerFactory.getLogger(CouponIssuanceServiceImpl.class);

    public FailedCouponRetryCountIncrease(final FailedIssuedCouponRepository failedIssuedCouponRepository) {
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCountIncrease(final UUID failedIssuedCouponId) {
        FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.findByIdWithLock(failedIssuedCouponId);

        if (failedCoupon == null) {
            log.warn("[FailedCouponRetryCountIncrease] 이미 처리되었거나 존재하지 않는 실패 이력입니다. ID: {}", failedIssuedCouponId);
            return;
        }

        failedCoupon.increaseRetryCount();
        log.info("[FailedCouponRetryCountIncrease] 재시도 횟수 증가 완료. ID: {}, retryCount: {}", failedIssuedCouponId, failedCoupon.getRetryCount());
    }
}
