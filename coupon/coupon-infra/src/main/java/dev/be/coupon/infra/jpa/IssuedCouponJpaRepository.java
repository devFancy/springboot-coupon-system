package dev.be.coupon.infra.jpa;

import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssuedCouponJpaRepository extends IssuedCouponRepository, JpaRepository<IssuedCoupon, UUID> {
    @Override
    boolean existsByUserIdAndCouponId(final UUID userId, final UUID couponId);

    @Override
    @Query("SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.couponId = :couponId")
    int countByCouponId(@Param("couponId") final UUID couponId);

    @Override
    IssuedCoupon save(final IssuedCoupon issuedCoupon);

    @Override
    Optional<IssuedCoupon> findByUserIdAndCouponId(final UUID userId, final UUID couponId);

    @Override
    List<IssuedCoupon> findAllByUserId(final UUID userId);
}
