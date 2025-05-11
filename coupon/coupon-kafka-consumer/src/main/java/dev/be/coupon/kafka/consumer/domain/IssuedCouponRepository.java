package dev.be.coupon.kafka.consumer.domain;


import java.util.UUID;

/**
 * coupon-api, coupon-kafka-consumer 양쪽에서 사용되는 발급 쿠폰 저장소입니다.
 *
 * 도메인 공유를 위한 현재 구조에서는 하나의 정의를 참조하고 있으며,
 * 추후 확장 시 도메인 전용 모듈(coupon-domain) 분리 및 의존성 정리가 필요할 수 있습니다.
 */
public interface IssuedCouponRepository {
    IssuedCoupon save(final IssuedCoupon issuedCoupon);

    int countByCouponId(final UUID couponId);
}
