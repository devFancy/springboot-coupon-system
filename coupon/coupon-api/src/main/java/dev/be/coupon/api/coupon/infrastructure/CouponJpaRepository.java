package dev.be.coupon.api.coupon.infrastructure;

import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponJpaRepository extends CouponRepository, JpaRepository<Coupon, UUID> {

    @Override
    Coupon save(final Coupon coupon);
}
