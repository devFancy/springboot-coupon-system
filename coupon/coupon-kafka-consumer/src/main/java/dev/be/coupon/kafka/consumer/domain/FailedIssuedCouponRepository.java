package dev.be.coupon.kafka.consumer.domain;

import java.util.List;
import java.util.UUID;

public interface FailedIssuedCouponRepository {

    FailedIssuedCoupon save(final FailedIssuedCoupon failedIssuedCoupon);

    List<FailedIssuedCoupon> findAllByIsResolvedFalse();

    FailedIssuedCoupon findByIdWithLock(UUID id);
}
