package dev.be.coupon.infra.jpa;

import dev.be.coupon.domain.coupon.CouponIssueFailedEvent;
import dev.be.coupon.domain.coupon.CouponIssueFailedEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponIssueFailedEventJpaRepository extends CouponIssueFailedEventRepository, JpaRepository<CouponIssueFailedEvent, UUID> {

    @Override
    CouponIssueFailedEvent save(final CouponIssueFailedEvent couponIssueFailedEvent);
}
