package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponIssueStatus;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.AppliedUserRepository;
import dev.be.coupon.infra.redis.CouponCacheRepository;
import dev.be.coupon.infra.redis.CouponCountRedisRepository;
import dev.be.coupon.infra.redis.CouponIssueFailureRepository;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.application.exception.CouponNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssueService {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRepository couponRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final AppliedUserRepository appliedUserRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final CouponIssueFailureRepository couponIssueFailureRepository;

    private final Logger log = LoggerFactory.getLogger(CouponIssueService.class);

    public CouponIssueService(final IssuedCouponRepository issuedCouponRepository,
                              final CouponRepository couponRepository,
                              final CouponCacheRepository couponCacheRepository,
                              final AppliedUserRepository appliedUserRepository,
                              final CouponCountRedisRepository couponCountRedisRepository,
                              final CouponIssueFailureRecorder couponIssueFailureRecorder,
                              final CouponIssueFailureRepository couponIssueFailureRepository) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRepository = couponRepository;
        this.couponCacheRepository = couponCacheRepository;
        this.appliedUserRepository = appliedUserRepository;
        this.couponCountRedisRepository = couponCountRedisRepository;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
        this.couponIssueFailureRepository = couponIssueFailureRepository;
    }

    @DistributedLock(key = "'coupon:' + #message.couponId() + ':user:' + #message.userId()", waitTime = 5, leaseTime = 30)
    public void issue(CouponIssueMessage message) {
        final UUID userId = message.userId();
        final UUID couponId = message.couponId();
        log.info("[Consumer] 쿠폰 발급 처리 시작 - userId: {}, couponId: {}", userId, couponId);

        try {
            final Coupon coupon = loadCouponWithCaching(couponId);
            coupon.validateIssuableStatus(LocalDateTime.now());

            if (isDuplicateRequest(couponId, userId)) {
                couponIssueFailureRepository.record(couponId, userId, CouponIssueStatus.FAILED_DUPLICATE);
                return;
            }

            if (isQuantityExceeded(couponId, userId, coupon.getTotalQuantity())) {
                couponIssueFailureRepository.record(couponId, userId, CouponIssueStatus.FAILED_QUANTITY);
                return;
            }

            saveIssuedCouponInTransaction(userId, couponId);

        } catch (Exception e) {
            log.error("[Consumer] 쿠폰 발급 처리 중 예상치 못한 오류 발생 - userId: {}, couponId: {}", userId, couponId, e);
            couponIssueFailureRecorder.record(userId, couponId);
            String countKey = "coupon_count:" + couponId;
            rollbackFullRedisOperations(couponId, userId, countKey);
        }
    }

    private boolean isDuplicateRequest(final UUID couponId, final UUID userId) {
        if (!appliedUserRepository.add(couponId, userId)) {
            log.info("[Consumer] 중복 발급 요청입니다. 이미 처리된 사용자입니다. - userId: {}", userId);
            return true;
        }
        return false;
    }

    private boolean isQuantityExceeded(final UUID couponId, final UUID userId, final int totalQuantity) {
        String countKey = "coupon_count:" + couponId;
        Long issuedCount = couponCountRedisRepository.increment(countKey);

        if (issuedCount > totalQuantity) {
            log.warn("[Consumer] 쿠폰 수량이 초과되었습니다. (현재 카운트: {})", issuedCount);
            rollbackFullRedisOperations(couponId, userId, countKey);
            return true;
        }
        return false;
    }

    @Transactional
    public void saveIssuedCouponInTransaction(UUID userId, UUID couponId) {
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("[Consumer] DB에 이미 발급된 쿠폰입니다. (정합성 불일치 가능성) - userId: {}", userId);
            return;
        }
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
        log.info("[Consumer] 쿠폰 발급 DB 저장 완료: {}", issuedCoupon);
    }

    private Coupon loadCouponWithCaching(final UUID couponId) {
        return couponCacheRepository.findById(couponId)
                .orElseGet(() -> {
                    Coupon fromDb = couponRepository.findById(couponId)
                            .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));
                    couponCacheRepository.save(fromDb);
                    return fromDb;
                });
    }

    private void rollbackFullRedisOperations(final UUID couponId, final UUID userId, final String countKey) {
        try {
            appliedUserRepository.remove(couponId, userId);
            couponCountRedisRepository.decrement(countKey);
            log.info("[Consumer] Redis 롤백 완료 - userId: {}", userId);
        } catch (Exception redisEx) {
            log.error("[Consumer] Redis 롤백 중 심각한 오류 발생. 데이터 불일치 가능성이 있습니다. - userId: {}", userId, redisEx);
        }
    }
}
