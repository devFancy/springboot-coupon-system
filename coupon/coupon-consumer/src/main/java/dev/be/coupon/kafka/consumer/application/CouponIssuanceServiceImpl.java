package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.CouponRedisCache;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.application.exception.CouponNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssuanceServiceImpl implements CouponIssuanceService {

    private final CouponRepository couponRepository;
    private final CouponRedisCache couponRedisCache;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final IssuedCouponSaver issuedCouponSaver;
    private final Logger log = LoggerFactory.getLogger(CouponIssuanceServiceImpl.class);

    public CouponIssuanceServiceImpl(final CouponRepository couponRepository,
                                     final CouponRedisCache couponRedisCache,
                                     final CouponIssueFailureRecorder couponIssueFailureRecorder,
                                     final IssuedCouponSaver issuedCouponSaver) {
        this.couponRepository = couponRepository;
        this.couponRedisCache = couponRedisCache;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
        this.issuedCouponSaver = issuedCouponSaver;
    }

    /**
     * 분산 락을 적용하여 쿠폰 발급을 처리하고 트랜잭션은 DB 저장 로직으로 분리되었습니다.
     * 순서: Lock -> (Non-Transactional Logic) -> Transactional Save -> Unlock
     */
    @Override
    @DistributedLock(key = "'coupon:' + #message.couponId()", waitTime = 5, leaseTime = 30)
    public void process(final CouponIssueMessage message) {
        final UUID userId = message.userId();
        final UUID couponId = message.couponId();

        log.info("쿠폰 발급 처리 시작 - userId: {}, couponId: {}", userId, couponId);

        try {
            final Coupon coupon = loadCouponWithCaching(couponId);
            coupon.validateIssuableStatus(LocalDateTime.now());

            issuedCouponSaver.save(userId, couponId);

        } catch (Exception e) {
            log.error("쿠폰 발급 처리 중 예상치 못한 오류 발생 - userId: {}, couponId: {}", userId, couponId, e);
            couponIssueFailureRecorder.record(userId, couponId);
        }
    }

    private Coupon loadCouponWithCaching(final UUID couponId) {
        return couponRedisCache.getCouponById(couponId)
                .orElseGet(() -> {
                    Coupon fromDb = couponRepository.findById(couponId)
                            .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다. ID: " + couponId));
                    couponRedisCache.save(fromDb);
                    return fromDb;
                });
    }
}
