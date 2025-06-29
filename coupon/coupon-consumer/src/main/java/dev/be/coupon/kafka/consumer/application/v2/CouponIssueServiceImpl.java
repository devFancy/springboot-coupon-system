package dev.be.coupon.kafka.consumer.application.v2;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.application.CouponIssueService;
import org.springframework.stereotype.Service;

@Service("couponIssueV2Service")
public class CouponIssueServiceImpl implements CouponIssueService {

    private final CouponTransactionalIssuer couponTransactionalIssuer;

    public CouponIssueServiceImpl(final CouponTransactionalIssuer couponTransactionalIssuer) {
        this.couponTransactionalIssuer = couponTransactionalIssuer;
    }

    // 순서: Lock -> Transaction(Begin -> Commit) -> Unlock
    @DistributedLock(key = "'coupon:' + #message.couponId()", waitTime = 5, leaseTime = 30)
    public void issue(final CouponIssueMessage message) {
        couponTransactionalIssuer.issueInTransaction(message);
    }
}
