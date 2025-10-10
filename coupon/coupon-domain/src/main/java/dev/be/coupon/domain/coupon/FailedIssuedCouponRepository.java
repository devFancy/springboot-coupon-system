package dev.be.coupon.domain.coupon;

import java.util.List;
import java.util.UUID;

/**
 * coupon-api, coupon-consumer 양쪽에서 사용되는 발급 쿠폰 저장소입니다.
 *
 * 이를 위해 도메인 전용 모듈(coupon-domain)에서 추가했습니다.
 */
public interface FailedIssuedCouponRepository {

    FailedIssuedCoupon save(final FailedIssuedCoupon failedIssuedCoupon);

    List<FailedIssuedCoupon> findAllByIsResolvedFalseAndRetryCountLessThan(int retryCount);

    FailedIssuedCoupon findByIdWithLock(UUID id);
}
