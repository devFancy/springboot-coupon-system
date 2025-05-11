package dev.be.coupon.kafka.consumer.infrastructure;

import dev.be.coupon.kafka.consumer.domain.IssuedCoupon;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IssuedCouponJpaRepository extends IssuedCouponRepository, JpaRepository<IssuedCoupon, UUID> {
    @Override
    IssuedCoupon save(final IssuedCoupon issuedCoupon);

    @Override
    @Query("SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.couponId = :couponId")
    int countByCouponId(@Param("couponId") final UUID couponId);
}
