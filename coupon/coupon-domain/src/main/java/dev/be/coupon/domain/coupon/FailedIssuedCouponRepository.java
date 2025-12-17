package dev.be.coupon.domain.coupon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

/**
 * coupon-api, coupon-consumer 양쪽에서 사용되는 발급 쿠폰 저장소입니다.
 * <p>
 * 이를 위해 도메인 전용 모듈(coupon-domain)에서 추가했습니다.
 */
public interface FailedIssuedCouponRepository {

    FailedIssuedCoupon save(final FailedIssuedCoupon failedIssuedCoupon);

    Slice<FailedIssuedCoupon> findAllByIsResolvedFalseAndRetryCountLessThan(int retryCount, Pageable pageable);

    FailedIssuedCoupon findByIdWithLock(UUID id);
}
