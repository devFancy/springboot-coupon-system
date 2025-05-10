package dev.be.coupon.api.coupon.infrastructure.jpa;

import dev.be.coupon.api.coupon.domain.FailedIssuedCoupon;
import dev.be.coupon.api.coupon.domain.FailedIssuedCouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailedIssuedCouponJpaRepository extends FailedIssuedCouponRepository, JpaRepository<FailedIssuedCoupon, UUID> {

    @Override
    FailedIssuedCoupon save(final FailedIssuedCoupon failedIssuedCoupon);
}
