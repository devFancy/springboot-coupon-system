package dev.be.coupon.kafka.consumer.infrastructure;

import dev.be.coupon.kafka.consumer.domain.IssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssuedCouponJpaRepository extends IssuedCouponRepository, JpaRepository<IssuedCoupon, UUID> {
    @Override
    IssuedCoupon save(final IssuedCoupon issuedCoupon);
}
