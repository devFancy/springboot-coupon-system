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

    /**
     * 쿠폰 발급 트랜잭션이 롤백되더라도, 실패 이력만큼은 별도의 트랜잭션으로 커밋하기 위해 사용합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(final UUID userId, final UUID couponId) {
        repository.save(new FailedIssuedCoupon(userId, couponId));
    }
}
