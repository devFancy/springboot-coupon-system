package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.isNull;

@Table(name = "issued_coupons",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
        })
@Entity
public class IssuedCoupon {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
    private UUID userId;

    @Column(name = "coupon_id", columnDefinition = "binary(16)", nullable = false)
    private UUID couponId;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected IssuedCoupon() {
    }

    public IssuedCoupon(final UUID userId, final UUID couponId) {
        this(UUID.randomUUID(), userId, couponId, false, LocalDateTime.now(), null);
    }

    public IssuedCoupon(final UUID id,
                        final UUID userId,
                        final UUID couponId,
                        final boolean used,
                        final LocalDateTime issuedAt,
                        final LocalDateTime usedAt) {
        validateIssuedCouponCreation(id, userId, couponId, issuedAt);
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.used = used;
        this.issuedAt = issuedAt;
        this.usedAt = usedAt;
    }

    public void use(final LocalDateTime usedAt) {
        if (this.used) {
            throw new CouponDomainException("이미 사용된 쿠폰입니다.");
        }
        this.used = true;
        this.usedAt = usedAt;
    }

    private void validateIssuedCouponCreation(
            final UUID id,
            final UUID userId,
            final UUID couponId,
            final LocalDateTime issuedAt) {
        if (isNull(id) || isNull(userId) || isNull(couponId) || isNull(issuedAt)) {
            throw new CouponDomainException("발급된 쿠폰 생성에 필요한 정보가 누락되었습니다.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IssuedCoupon that)) return false;
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

    public boolean isUsed() {
        return used;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }
}
