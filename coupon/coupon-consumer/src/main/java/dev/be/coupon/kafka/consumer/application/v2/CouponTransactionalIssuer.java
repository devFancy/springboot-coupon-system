package dev.be.coupon.kafka.consumer.application.v2;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.v2.CouponSoldOutCacheRepository;
import dev.be.coupon.infra.redis.v2.CouponV2CacheRepository;
import dev.be.coupon.kafka.consumer.application.CouponIssueFailureRecorder;
import dev.be.coupon.kafka.consumer.application.v2.exception.CouponNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CouponTransactionalIssuer {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;
    private final CouponV2CacheRepository couponV2CacheRepository;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final CouponSoldOutCacheRepository couponSoldOutCacheRepository;

    private final Logger log = LoggerFactory.getLogger(CouponTransactionalIssuer.class);

    public CouponTransactionalIssuer(final IssuedCouponRepository issuedCouponRepository,
                                     final CouponRepository couponRepository,
                                     @Qualifier("CouponV2CacheRepository") final CouponV2CacheRepository couponV2CacheRepository,
                                     final CouponIssueFailureRecorder couponIssueFailureRecorder,
                                     final CouponSoldOutCacheRepository couponSoldOutCacheRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRepository = couponRepository;
        this.couponV2CacheRepository = couponV2CacheRepository;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
        this.couponSoldOutCacheRepository = couponSoldOutCacheRepository;
    }

    @Transactional
    public void issueInTransaction(final CouponIssueMessage message) {
        final UUID userId = message.userId();
        final UUID couponId = message.couponId();

        if (couponSoldOutCacheRepository.isSoldOut(couponId)) {
            log.warn("[Consumer-V2] '매진' 캐시 확인 후 발급 중단. couponId: {}", couponId);
            return;
        }
        log.info("[Consumer-V2] 쿠폰 발급 처리 시작 - userId: {}, couponId: {}", userId, couponId);

        try {
            final Coupon coupon = loadCouponWithCaching(couponId);
            coupon.validateIssuableStatus(LocalDateTime.now());

            int issuedCount = issuedCouponRepository.countByCouponId(couponId);
            if (issuedCount >= coupon.getTotalQuantity()) {
                log.warn("[Consumer-V2] 최종 확인 단계에서 수량 초과 확인됨. 발급을 중단합니다. couponId: {}, issuedCount: {}", couponId, issuedCount);
                couponSoldOutCacheRepository.setSoldOut(couponId);
                return;
            }
            saveIssuedCouponInTransaction(userId, couponId);

        } catch (Exception e) {
            log.error("[Consumer-V2] 쿠폰 발급 처리 중 예상치 못한 오류 발생 - userId: {}, couponId: {}", userId, couponId, e);
            couponIssueFailureRecorder.record(userId, couponId);
        }
    }

    private void saveIssuedCouponInTransaction(final UUID userId, final UUID couponId) {
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("[Consumer-V2] DB에 이미 발급된 쿠폰입니다. (정합성 불일치 가능성) - userId: {}", userId);
            return;
        }
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
        log.info("[Consumer-V2] 쿠폰 발급 DB 저장 완료: {}", issuedCoupon);
    }

    private Coupon loadCouponWithCaching(final UUID couponId) {
        return couponV2CacheRepository.findById(couponId)
                .orElseGet(() -> {
                    Coupon fromDb = couponRepository.findById(couponId)
                            .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));
                    couponV2CacheRepository.save(fromDb);
                    return fromDb;
                });
    }
}
