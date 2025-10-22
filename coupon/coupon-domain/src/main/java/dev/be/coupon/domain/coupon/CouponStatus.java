package dev.be.coupon.domain.coupon;

/**
 * 쿠폰 상태를 정의하는 Enum.
 * PENDING(대기), ACTIVE(사용 가능), EXPIRED(만료됨), DISABLED(비활성화)
 * DISABLED는 관리자용
 */
public enum CouponStatus {
    PENDING, ACTIVE, EXPIRED, DISABLED;
}
