package dev.be.coupon.infra.jpa;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FailedIssuedCouponJpaRepository extends FailedIssuedCouponRepository, JpaRepository<FailedIssuedCoupon, UUID> {

    @Override
    FailedIssuedCoupon save(final FailedIssuedCoupon failedIssuedCoupon);

    @Override
    @Query("select f from FailedIssuedCoupon f where f.isResolved = false and f.retryCount < :retryCount")
    List<FailedIssuedCoupon> findAllByIsResolvedFalseAndRetryCountLessThan(final int retryCount);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FailedIssuedCoupon f where f.id = :id")
    FailedIssuedCoupon findByIdWithLock(@Param("id") final UUID id);
}
