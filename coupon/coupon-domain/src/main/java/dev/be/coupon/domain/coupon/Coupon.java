package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.domain.coupon.vo.CouponName;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.isNull;

@Table(name = "coupons")
@Entity
public class Coupon {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Embedded
    private CouponName couponName;

    @Column(name = "coupon_type", nullable = false, columnDefinition = "varchar(255)")
    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    @Column(name = "coupon_discount_type", nullable = false, columnDefinition = "varchar(255)")
    @Enumerated(EnumType.STRING)
    private CouponDiscountType couponDiscountType;

    @Column(name = "coupon_discount_value", nullable = false)
    private BigDecimal couponDiscountValue;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "coupon_status", nullable = false, columnDefinition = "varchar(255)")
    @Enumerated(EnumType.STRING)
    private CouponStatus couponStatus;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    protected Coupon() {
    }

    public Coupon(final String name,
                  final CouponType couponType,
                  final CouponDiscountType couponDiscountType,
                  final BigDecimal couponDiscountValue,
                  final int totalQuantity,
                  final LocalDateTime expiredAt) {
        this(UUID.randomUUID(), new CouponName(name), couponType, couponDiscountType, couponDiscountValue, CouponStatus.ACTIVE, totalQuantity, expiredAt);
    }

    public Coupon(final UUID id,
                  final CouponName couponName,
                  final CouponType couponType,
                  final CouponDiscountType couponDiscountType,
                  final BigDecimal couponDiscountValue,
                  final CouponStatus couponStatus,
                  final int totalQuantity,
                  final LocalDateTime expiredAt) {

        validateCouponCreation(id, couponName, couponType, couponDiscountType, couponDiscountValue, couponStatus, totalQuantity, expiredAt);
        validateCouponTotalQuantity(totalQuantity);
        validateCouponValidPeriod(expiredAt, couponStatus);
        this.id = id;
        this.couponName = couponName;
        this.couponType = couponType;
        this.couponDiscountType = couponDiscountType;
        this.couponDiscountValue = couponDiscountValue;
        this.totalQuantity = totalQuantity;
        this.couponStatus = couponStatus;
        this.expiredAt = expiredAt;
    }

    private void validateCouponCreation(
            final UUID couponId,
            final CouponName couponName,
            final CouponType couponType,
            final CouponDiscountType couponDiscountType,
            final BigDecimal couponDiscountValue,
            final CouponStatus couponStatus,
            final int totalQuantity,
            final LocalDateTime expiredAt
    ) {
        if (isNull(couponId) || isNull(couponName) || isNull(couponType)
                || isNull(couponDiscountType) || isNull(couponDiscountValue)
                || isNull(totalQuantity) || isNull(couponStatus)
                || isNull(expiredAt)) {
            throw new CouponDomainException("쿠폰 생성에 필요한 정보가 누락되었습니다.");
        }
    }

    private void validateCouponTotalQuantity(final int totalQuantity) {
        if (totalQuantity < 1) {
            throw new CouponDomainException("쿠폰 발급 수량은 1 이상이야 합니다.");
        }
    }

    private void validateCouponValidPeriod(final LocalDateTime expiredAt, final CouponStatus status) {
        if (status == CouponStatus.ACTIVE && expiredAt.isBefore(LocalDateTime.now())) {
            throw new CouponDomainException("ACTIVE 상태 쿠폰의 만료일은 현재 시간보다 이후여야 합니다.");
        }
    }

    public void validateStatusIsActive(final LocalDateTime now) {
        updateStatusBasedOnDate(now);
        if (this.couponStatus != CouponStatus.ACTIVE) {
            throw new CouponDomainException("쿠폰이 현재 사용 가능한 상태가 아닙니다. 현재 상태: " + this.couponStatus);
        }
    }

    private void updateStatusBasedOnDate(final LocalDateTime now) {
        if (this.couponStatus == CouponStatus.EXPIRED || this.couponStatus == CouponStatus.DISABLED) {
            return;
        }

        if (now.isAfter(this.expiredAt)) {
            this.couponStatus = CouponStatus.EXPIRED;
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

    public CouponDiscountType getCouponDiscountType() {
        return couponDiscountType;
    }

    public BigDecimal getCouponDiscountValue() {
        return couponDiscountValue;
    }

    public CouponStatus getCouponStatus() {
        return couponStatus;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }
}
