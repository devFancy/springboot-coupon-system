package dev.be.coupon.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * FailedIssuedCoupon (발급 실패 이력)
 * > 쿠폰 발급 처리 도중 실패한 이력을 저장하고, 추후 재처리 스케줄러에서 재시도하는 도메인입니다.
 * <p>
 * | 한글명       | 영문명     | 설명                                                         |
 * |------------|----------|------------------------------------------------------------|
 * | 실패 ID     | id       | 실패 이력의 고유 식별자 (UUID)                                     |
 * | 사용자 ID   | userId   | 발급 실패가 발생한 사용자 식별자                                  |
 * | 쿠폰 ID     | couponId | 발급에 실패한 쿠폰의 식별자                                      |
 * | 실패 일시    | failedAt | 쿠폰 발급 실패가 발생한 시점                                     |
 * | 재시도 횟수  | retryCount | 해당 실패 이력에 대해 재시도한 횟수                                 |
 * | 해결 여부    | isResolved | 실패 건이 정상적으로 재처리되어 해결되었는지 여부 (`true` = 해결됨) |
 * <p>
 * 도메인 모듈(coupon-domain)에 위치하며,
 * coupon-api, coupon-kafka-consumer 모듈에서 함께 사용됩니다.
 */
@Table(name = "failed_issued_coupons")
@Entity
public class FailedIssuedCoupon {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
    private UUID userId;

    @Column(name = "coupon_id", columnDefinition = "binary(16)", nullable = false)
    private UUID couponId;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "is_resolved", nullable = false)
    private boolean isResolved = false;

    public FailedIssuedCoupon() {
    }

    public FailedIssuedCoupon(final UUID userId, final UUID couponId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.couponId = couponId;
        this.failedAt = LocalDateTime.now();
    }

    public void markResolved() {
        this.isResolved = true;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FailedIssuedCoupon that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean isResolved() {
        return isResolved;
    }
}
