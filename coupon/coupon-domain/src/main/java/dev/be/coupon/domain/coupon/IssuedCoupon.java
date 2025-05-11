package dev.be.coupon.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import static java.util.Objects.isNull;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * IssuedCoupon (발급된 쿠폰)
 * > 사용자에게 발급된 쿠폰을 관리하며, 발급, 중복 방지, 사용 처리 등의 비즈니스 로직을 포함하는 도메인입니다.
 * <p>
 * | 한글명 | 영문명 | 설명 |
 * | --- | --- | --- |
 * | 발급 쿠폰 ID | issuedCouponId | 사용자에게 발급된 쿠폰의 고유 식별자 |
 * | 사용자 ID | userId | 해당 쿠폰을 발급받은 사용자 |
 * | 쿠폰 ID | couponId | 어떤 쿠폰이 발급되었는지 참조 |
 * | 발급일 | issuedAt | 쿠폰이 발급된 시점 |
 * | 사용 여부 | used | 쿠폰이 사용되었는지 여부 (boolean) |
 * | 사용일 | usedAt | 쿠폰 사용 시점 |
 * | 중복 방지 키 | userId+couponId | 복합 유니크 키로 중복 발급 방지 |
 */

/**
 * 도메인 모듈(coupon-domain)에 위치하며,
 * coupon-api, coupon-kafka-consumer 모듈에서 함께 사용됩니다.
 *
 * 중복 발급 방지를 위해 userId + couponId 조합에 대해 유니크 제약 조건이 적용됩니다.
 */
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
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
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
            throw new IllegalArgumentException("발급된 쿠폰 생성에 필요한 정보가 누락되었습니다.");
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
