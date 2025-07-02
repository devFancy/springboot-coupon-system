package dev.be.coupon.infra.jpa;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends CouponRepository, JpaRepository<Coupon, UUID> {

    @Override
    Coupon save(final Coupon coupon);

    @Override
    Optional<Coupon> findById(final UUID couponId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :couponId")
    Optional<Coupon> findByIdWithPessimisticLock(UUID couponId);
}
