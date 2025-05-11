package dev.be.coupon.kafka.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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
