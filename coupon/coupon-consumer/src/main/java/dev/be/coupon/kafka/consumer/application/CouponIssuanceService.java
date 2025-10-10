package dev.be.coupon.kafka.consumer.application;

import java.util.UUID;

public interface CouponIssuanceService {

    // 신규 쿠폰 발급
    void issue(final UUID userId, final UUID couponId);

    // 재처리 쿠폰 발급
    void reissue(final UUID userId, final UUID couponId, final UUID failedIssuedCouponId);

    // 최초 실패 기록
    void recordFailure(final UUID userId, final UUID couponId);
}
