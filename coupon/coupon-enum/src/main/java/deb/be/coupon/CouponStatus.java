package deb.be.coupon;

import java.time.LocalDateTime;

/**
 * 쿠폰 상태를 정의하는 Enum.
 * ACTIVE(사용 가능), EXPIRED(만료됨), DISABLED(비활성화)
 */
public enum CouponStatus {
    ACTIVE, EXPIRED, DISABLED;

    public static CouponStatus decideStatus(final LocalDateTime now,
                                            final LocalDateTime validFrom,
                                            final LocalDateTime validUntil) {
        if (now.isBefore(validFrom)) {
            return DISABLED;
        }
        if (now.isAfter(validUntil)) {
            return EXPIRED;
        }
        return ACTIVE;
    }
}
