package dev.be.coupon.domain.coupon;

import java.util.Optional;
import java.util.UUID;

/**
 * coupon-api, coupon-consumer 양쪽에서 사용되는 발급 쿠폰 저장소입니다.
 *
 * 이를 위해 도메인 전용 모듈(coupon-domain)에서 추가했습니다.
 */
public interface IssuedCouponRepository {

    boolean existsByUserIdAndCouponId(final UUID userId, final UUID couponId);

    int countByCouponId(final UUID couponId);
    IssuedCoupon save(final IssuedCoupon issuedCoupon);

    // 쿠폰 사용 시, 특정 사용자의 특정 쿠폰 정의 ID에 해당하는 발급 쿠폰 조회
    Optional<IssuedCoupon> findByUserIdAndCouponId(final UUID userId, final UUID couponId);
}
