package dev.be.coupon.api.coupon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
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

    public FailedIssuedCoupon() {
    }

    public FailedIssuedCoupon(final UUID userId, final UUID couponId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.couponId = couponId;
        this.failedAt = LocalDateTime.now();
    }
}
