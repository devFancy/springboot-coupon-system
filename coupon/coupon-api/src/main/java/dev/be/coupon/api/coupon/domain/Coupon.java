package dev.be.coupon.api.coupon.domain;

import deb.be.coupon.CouponStatus;
import deb.be.coupon.CouponType;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;
import dev.be.coupon.api.coupon.domain.vo.CouponName;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import static java.util.Objects.isNull;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Table(name = "coupons")
@Entity
public class Coupon {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Embedded
    private CouponName couponName;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponStatus couponStatus;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    protected Coupon() {
    }
    public Coupon(final String name,
                  final CouponType couponType,
                  final int totalQuantity,
                  final LocalDateTime validFrom,
                  final LocalDateTime validUntil) {
        this(UUID.randomUUID(), new CouponName(name), couponType, totalQuantity, CouponStatus.ACTIVE, validFrom, validUntil);
    }

    public Coupon(final UUID id,
                  final CouponName couponName,
                  final CouponType couponType,
                  final int totalQuantity,
                  final CouponStatus status,
                  final LocalDateTime validFrom,
                  final LocalDateTime validUntil) {

        validateCouponCreation(id, couponName, couponType, totalQuantity, status, validFrom, validUntil);

        this.id = id;
        this.couponName = couponName;
        this.couponType = couponType;
        this.totalQuantity = totalQuantity;
        this.couponStatus = status;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    private void validateCouponCreation(
            final UUID couponId,
            final CouponName couponName,
            final CouponType couponType,
            final int totalQuantity,
            final CouponStatus couponStatus,
            final LocalDateTime validFrom,
            final LocalDateTime validUntil
    ) {
        if (isNull(couponId) || isNull(couponName) || isNull(couponType)
                || isNull(totalQuantity) || isNull(couponStatus)
        || isNull(validFrom) || isNull(validUntil)) {
            throw new InvalidCouponException("쿠폰 생성에 필요한 정보가 누락되었습니다.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coupon coupon)) return false;
        return Objects.equals(getId(), coupon.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    public UUID getId() {
        return id;
    }

    public CouponName getCouponName() {
        return couponName;
    }

    public CouponType getCouponType() {
        return couponType;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public CouponStatus getCouponStatus() {
        return couponStatus;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }
}
