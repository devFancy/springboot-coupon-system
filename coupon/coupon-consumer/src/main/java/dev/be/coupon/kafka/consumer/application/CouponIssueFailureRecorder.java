package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CouponIssueFailureRecorder {
    private final FailedIssuedCouponRepository repository;

    public CouponIssueFailureRecorder(FailedIssuedCouponRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(final UUID userId, final UUID couponId) {
        repository.save(new FailedIssuedCoupon(userId, couponId));
    }
}
