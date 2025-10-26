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
import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Coupon (쿠폰)
 * > 쿠폰의 템플릿을 정의하고, 쿠폰의 속성 및 정책을 관리하는 도메인입니다.
 * <p>
 * | 한글명 | 영문명 | 설명 |
 * | --- | --- | --- |
 * | 쿠폰 ID | couponId | 쿠폰 정의의 고유 식별자 (UUID) |
 * | 쿠폰 이름 | couponName | 쿠폰 제목 |
 * | 쿠폰 타입 | couponType | 치킨/햄버거/피자 등 |
 * | 쿠폰 할인 유형 | couponDiscountType  | FIXED(정액), PERCENTAGE(정률) |
 * | 쿠폰 할인 금액 | couponDiscountValue | 할인 금액(5,000원), 할인율 |
 * | 쿠폰 상태 | couponStatus | `PENDING`(대기), `ACTIVE`(사용 가능), `EXPIRED`(만료됨), `DISABLED`(비활성화) 등 |
 * | 쿠폰 총 발급 수량 | totalQuantity | 발급 가능한 총 수량 |
 * | 유효 기간일 | expiredAt | 유효 기간일 (이 시간 이후로 만료됨) |
 */
@Table(name = "coupons")
@Entity
public class Coupon {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    @Embedded
    private CouponName couponName;

    @Column(name = "coupon_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    @Column(name = "coupon_discount_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponDiscountType couponDiscountType;

    @Column(name = "coupon_discount_value", nullable = false)
    private BigDecimal couponDiscountValue;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "coupon_status", nullable = false, columnDefinition = "varchar(255)")
    @Enumerated(EnumType.STRING)
    private CouponStatus couponStatus; // 쿠폰 발급시 기본값으로 사용 가능(ACTIVE) 으로 설정

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
        // ACTIVE 상태로 생성하는데 만료일이 이미 과거라면 예외 발생
        if (status == CouponStatus.ACTIVE && expiredAt.isBefore(LocalDateTime.now())) {
            throw new CouponDomainException("ACTIVE 상태 쿠폰의 만료일은 현재 시간보다 이후여야 합니다.");
        }
    }

    public void validateStatusIsActive(final LocalDateTime now) {
        updateStatusBasedOnDate(now);
        if (this.couponStatus != CouponStatus.ACTIVE) {
            throw new CouponDomainException("쿠폰 상태가 활성 상태가 아닙니다. 현재 상태: " + this.couponStatus);
        }
    }

    /**
     * 쿠폰의 상태를 현재 시간에 맞게 동기화합니다.
     * 만료일이 지난 쿠폰을 `EXPIRED`로 처리하며, 이미 만료되었거나 비활성화된 쿠폰의 상태는 변경하지 않습니다.
     */
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
