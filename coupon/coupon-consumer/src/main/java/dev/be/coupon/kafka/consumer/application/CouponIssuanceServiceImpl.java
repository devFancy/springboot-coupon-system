package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CouponIssuanceServiceImpl implements CouponIssuanceService {

    private final IssuedCouponRepository issuedCouponRepository;
    private final FailedIssuedCouponRepository failedIssuedCouponRepository;

    private final Logger log = LoggerFactory.getLogger(CouponIssuanceServiceImpl.class);

    public CouponIssuanceServiceImpl(IssuedCouponRepository issuedCouponRepository,
                                     FailedIssuedCouponRepository failedIssuedCouponRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
    }

    @Override
    @Transactional
    public void issue(final UUID userId, final UUID couponId) {
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("DB에 이미 발급된 쿠폰입니다. - userId: {}", userId);
            return;
        }
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
        log.info("쿠폰 발급 DB 저장 완료: {}", issuedCoupon);
    }

    @Override
    @Transactional
    public void reissue(final UUID userId, final UUID couponId, final UUID failedIssuedCouponId) {
        // 1. 쿠폰 발급 저장
        if (!issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            issuedCouponRepository.save(new IssuedCoupon(userId, couponId));
            log.info("쿠폰 발급 DB 저장 완료");
        }

        // 2. 실패 이력 업데이트
        FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.findByIdWithLock(failedIssuedCouponId);
        if (failedCoupon != null) {
            failedCoupon.markResolved();
            log.info("실패 이력에 '해결됨'으로 업데이트");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(final UUID userId, final UUID couponId) {
        failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));
    }
}
