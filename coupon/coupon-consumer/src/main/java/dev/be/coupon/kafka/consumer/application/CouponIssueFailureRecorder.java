package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Kafka Consumer에서 쿠폰 발급 실패 이력(FailedIssuedCoupon) 을 저장하는 컴포넌트
@Component
public class CouponIssueFailureRecorder {
    private final FailedIssuedCouponRepository repository;

    public CouponIssueFailureRecorder(FailedIssuedCouponRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, UUID couponId) {
        repository.save(new FailedIssuedCoupon(userId, couponId));
    }
}
