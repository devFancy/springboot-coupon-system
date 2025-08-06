package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class IssuedCouponSaver {

    private final IssuedCouponRepository issuedCouponRepository;
    private final Logger log = LoggerFactory.getLogger(IssuedCouponSaver.class);

    public IssuedCouponSaver(final IssuedCouponRepository issuedCouponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
    }


    @Transactional
    public void save(final UUID userId, final UUID couponId) {
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("DB에 이미 발급된 쿠폰입니다. - userId: {}", userId);
            return;
        }
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
        log.info("쿠폰 발급 DB 저장 완료: {}", issuedCoupon);
    }
}
