package dev.be.coupon.api.coupon.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 스케줄러 처리 도중 에러 발생시 재시도 횟수 증가
@Component
public class CouponRetryFailureHandler {

    private final FailedIssuedCouponRepository failedIssuedCouponRepository;

    public CouponRetryFailureHandler(final FailedIssuedCouponRepository failedIssuedCouponRepository) {
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
    }

    @Transactional
    public void handleFailure(final UUID failedIssuedCouponId) {
        FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.findByIdWithLock(failedIssuedCouponId);
        if (failedCoupon != null) {
            failedCoupon.increaseRetryCount();
        }
    }
}
