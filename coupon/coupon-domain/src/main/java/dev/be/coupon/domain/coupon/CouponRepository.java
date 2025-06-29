package dev.be.coupon.domain.coupon;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {

    Coupon save(final Coupon coupon);

    Optional<Coupon> findById(final UUID couponId);

    /**
     * 현재 시간을 기준으로 'ACTIVE' 상태이며 발급 가능한 모든 쿠폰 목록을 조회합니다.
     * Coupon 엔티티의 실제 필드명(couponStatus, validFrom, validUntil)을 사용합니다.
     * @param couponStatus 조회할 쿠폰 상태 (e.g., CouponStatus.ACTIVE)
     * @param now 현재 시간 (validFrom과 validUntil 비교용)
     * @return 발급 가능한 쿠폰 리스트
     */
    @Query("SELECT c FROM Coupon c " +
            "WHERE c.couponStatus = :status " +
            "AND c.validFrom <= :now " +
            "AND c.validUntil >= :now")
    List<Coupon> findAvailableCoupons(@Param("status") CouponStatus status, @Param("now") LocalDateTime now);
}
