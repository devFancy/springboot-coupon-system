package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.isNull;

@Table(name = "coupon_issue_failed_events", indexes = {@Index(name = "idx_issue_failed_status", columnList = "status"),
        @Index(name = "idx_issue_failed_user", columnList = "userId")
})
@Entity
public class CouponIssueFailedEvent {

    @Column(name = "id", columnDefinition = "binary(16)")
    @Id
    private UUID id;

    private UUID userId;
    private UUID couponId;

    @Column(columnDefinition = "TEXT")
    private String payload; // 원본 메시지 JSON

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255)")
    private FailedEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CouponIssueFailedEvent() {
    }

    public CouponIssueFailedEvent(final UUID userId, final UUID couponId, final String payload, final String errorMessage) {
        this(UUID.randomUUID(), userId, couponId, payload, errorMessage, FailedEventStatus.READY, LocalDateTime.now());
    }

    public CouponIssueFailedEvent(final UUID id, final UUID userId, final UUID couponId, final String payload, final String errorMessage, final FailedEventStatus status, final LocalDateTime createdAt) {
        validateFailedEvent(id, userId, couponId, payload, errorMessage, status, createdAt);
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.status = status;
        this.createdAt = createdAt;
    }

    private void validateFailedEvent(final UUID failedCouponId, final UUID userId, final UUID couponId, final String payload, final String errorMessage, final FailedEventStatus status, final LocalDateTime createdAt) {
        if (isNull(failedCouponId) || isNull(userId) || isNull(couponId) || isNull(payload) || isNull(errorMessage) || isNull(status) || isNull(createdAt)) {
            throw new CouponDomainException("실패 이력에 필요한 정보가 누락되었습니다.");
        }
    }

    public void markAsProcessed() {
        this.status = FailedEventStatus.PROCESSED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CouponIssueFailedEvent that = (CouponIssueFailedEvent) o;
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

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public FailedEventStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
