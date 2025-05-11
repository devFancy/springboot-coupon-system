package dev.be.coupon.domain.coupon.infrastructure;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends CouponRepository, JpaRepository<Coupon, UUID> {

    @Override
    Coupon save(final Coupon coupon);

    @Override
    Optional<Coupon> findById(final UUID couponId);
}
