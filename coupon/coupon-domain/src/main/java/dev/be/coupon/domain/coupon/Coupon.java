package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponNotCurrentlyUsableException;
import dev.be.coupon.domain.coupon.exception.CouponNotIssuableException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponPeriodException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponQuantityException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponStatusException;
import dev.be.coupon.domain.coupon.vo.CouponName;
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
 * | 쿠폰 상태 | status | `PENDING`(대기), `ACTIVE`(사용 가능), `EXPIRED`(만료됨), `DISABLED`(비활성화) 등 |
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
        validateCouponTotalQuantity(totalQuantity);
        validateCouponValidPeriod(validFrom, validUntil);
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

    private void validateCouponTotalQuantity(final int totalQuantity) {
        if (totalQuantity < 1) {
            throw new InvalidCouponQuantityException("쿠폰 발급 수량은 1 이상이야 합니다");
        }
    }

    private void validateCouponValidPeriod(final LocalDateTime validFrom, final LocalDateTime validUntil) {
        if (validFrom.isAfter(validUntil)) {
            throw new InvalidCouponPeriodException("쿠폰의 유효 시작일은 만료일보다 이전이어야 합니다.");
        }
    }

    public void validateIssuableStatus(final LocalDateTime now) {
        try {
            validateStatusIsActive(now);
        } catch (InvalidCouponStatusException e) {
            throw new CouponNotIssuableException("현재 쿠폰은 발급 가능한 상태가 아닙니다. 상태:" + this.couponStatus);
        }
    }

    /**
     * 쿠폰이 현재 사용 가능한지 검증합니다.
     * 상태를 업데이트하고, ACTIVE 상태가 아니거나 유효기간이 아니면 예외를 발생시킵니다.
     */
    public void validateUsableStatus(final LocalDateTime now) {
        try {
            validateStatusIsActive(now);
        } catch (InvalidCouponStatusException e) {
            throw new CouponNotCurrentlyUsableException("현재 쿠폰은 사용 가능한 상태가 아닙니다.");
        }
    }

    public void validateStatusIsActive(final LocalDateTime now) {
        updateStatusBasedOnDate(now);
        if (this.couponStatus != CouponStatus.ACTIVE) {
            throw new InvalidCouponStatusException("쿠폰 상태가 활성 상태가 아닙니다. 현재 상태: " + this.couponStatus);
        }
    }

    /**
     * 현재 시점을 기준으로 쿠폰 상태를 갱신합니다.
     * <p>
     * - 유효 시작일 ~ 유효 종료일 사이이면 ACTIVE
     * - 유효 종료일이 지났으면 EXPIRED
     * <p>
     * 추후 확장 고려:
     * - 매일 자정 등 주기적으로 실행되는 스케줄러에서 전체 쿠폰 상태 일괄 갱신
     * - 또는 쿠폰 조회/발급 시점에 동적으로 상태 갱신
     */
    private void updateStatusBasedOnDate(final LocalDateTime now) {
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
