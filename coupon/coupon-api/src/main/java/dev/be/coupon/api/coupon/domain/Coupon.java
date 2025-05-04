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

/**
 * Coupon (쿠폰 정의)
 * > 쿠폰 발급의 템플릿을 정의하고, 쿠폰의 속성 및 정책을 관리하는 도메인입니다.
 * <p>
 * | 한글명 | 영문명 | 설명 |
 * | --- | --- | --- |
 * | 쿠폰 ID | couponId | 쿠폰 정의의 고유 식별자 (UUID) |
 * | 쿠폰 이름 | couponName | 쿠폰 제목 또는 설명 |
 * | 쿠폰 타입 | couponType | `CHICKEN`, `PIZZA`, `BURGER` 등 |
 * | 발급 수량 | totalQuantity | 발급 가능한 총 수량 |
 * | 쿠폰 상태 | status | `ACTIVE`(사용 가능), `EXPIRED`(만료됨), `DISABLED`(비활성화) 등 |
 * | 유효 시작일 | validFrom | 사용 가능 시작일 |
 * | 유효 종료일 | validUntil | 만료일 |
 */
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
    private CouponStatus couponStatus; // 쿠폰 발급시 기본값으로 사용 가능(ACTIVE) 으로 설정

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

    public void updateStatusBasedOnDate(final LocalDateTime now) {
        this.couponStatus = CouponStatus.decideStatus(now, validFrom, validUntil);
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
