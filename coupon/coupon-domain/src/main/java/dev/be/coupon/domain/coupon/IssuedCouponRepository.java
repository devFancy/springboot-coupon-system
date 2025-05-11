package dev.be.coupon.domain.coupon;

import java.util.UUID;

/**
 * coupon-api, coupon-kafka-consumer 양쪽에서 사용되는 발급 쿠폰 저장소입니다.
 *
 * 이를 위해 도메인 전용 모듈(coupon-domain)에서 추가했습니다.
 */
public interface IssuedCouponRepository {

    boolean existsByUserIdAndCouponId(final UUID userId, final UUID couponId);

    int countByCouponId(final UUID couponId);
    IssuedCoupon save(final IssuedCoupon issuedCoupon);
}
