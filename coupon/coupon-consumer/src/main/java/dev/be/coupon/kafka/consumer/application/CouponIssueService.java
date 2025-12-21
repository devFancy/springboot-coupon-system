package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponIssueService(final CouponRepository couponRepository,
                              final IssuedCouponRepository issuedCouponRepository) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    @Transactional
    public void issue(final UUID userId, final UUID couponId) {
        validateCoupon(couponId);
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CouponConsumerException(ErrorType.COUPON_ALREADY_ISSUED);
        }
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
    }

    private void validateCoupon(final UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponConsumerException(ErrorType.COUPON_NOT_FOUND));

        try {
            coupon.validateStatusIsActive(LocalDateTime.now());
        } catch (CouponDomainException e) {
            throw new CouponConsumerException(ErrorType.COUPON_STATUS_IS_NOT_ACTIVE);
        }
    }
}
