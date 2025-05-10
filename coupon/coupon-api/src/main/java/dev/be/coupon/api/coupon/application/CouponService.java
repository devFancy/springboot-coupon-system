package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.exception.CouponNotFoundException;
import dev.be.coupon.api.coupon.application.exception.InvalidIssuedCouponException;
import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.FailedIssuedCoupon;
import dev.be.coupon.api.coupon.domain.FailedIssuedCouponRepository;
import dev.be.coupon.api.coupon.domain.IssuedCouponRepository;
import dev.be.coupon.api.coupon.domain.UserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import dev.be.coupon.api.coupon.infrastructure.kafka.producer.CouponIssueProducer;
import dev.be.coupon.api.coupon.infrastructure.redis.AppliedUserRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCacheRepository;
import dev.be.coupon.api.coupon.infrastructure.redis.CouponCountRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final IssuedCouponRepository issuedCouponRepository;
    private final AppliedUserRepository appliedUserRepository;
    private final FailedIssuedCouponRepository failedIssuedCouponRepository;
    private final UserRoleChecker userRoleChecker;

    private final Logger log = LoggerFactory.getLogger(CouponService.class);

    public CouponService(final CouponRepository couponRepository,
                         final CouponCacheRepository couponCacheRepository,
                         final CouponCountRedisRepository couponCountRedisRepository,
                         final CouponIssueProducer couponIssueProducer,
                         final IssuedCouponRepository issuedCouponRepository,
                         final AppliedUserRepository appliedUserRepository,
                         final FailedIssuedCouponRepository failedIssuedCouponRepository,
                         final UserRoleChecker userRoleChecker) {
        this.couponRepository = couponRepository;
        this.couponCacheRepository = couponCacheRepository;
        this.couponCountRedisRepository = couponCountRedisRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.issuedCouponRepository = issuedCouponRepository;
        this.appliedUserRepository = appliedUserRepository;
        this.failedIssuedCouponRepository = failedIssuedCouponRepository;
        this.userRoleChecker = userRoleChecker;
    }

    public CouponCreateResult create(
            final CouponCreateCommand command) {

        if (!userRoleChecker.isAdmin(command.userId())) {
            throw new UnauthorizedAccessException("쿠폰 생성은 관리자만 가능합니다.");
        }

        final Coupon coupon = command.toDomain();
        return CouponCreateResult.from(couponRepository.save(coupon));
    }

    /**
     * 쿠폰 발급 요청 처리 흐름(주요 내용)
     * <p>
     * 1. 분산 락: Redis SETNX 기반 사용자 단위 락 (lock:coupon:{couponId}:user:{userId})
     * - 동일 사용자의 중복 요청을 직렬화하여 race condition 방지
     * <p>
     * 2. 중복 발급 방지: Redis Set(applied_user:{couponId})
     * - 최초 요청만 true, 이후는 중복으로 간주하고 차단
     * - 중복이면 발급 수량 증가를 수행하지 않음 (정확한 수량 보장 목적)
     * <p>
     * 3. 수량 제한: Redis INCR(coupon_count:{couponId})
     * - 최초 요청인 경우에만 수량 증가
     * - 발급 수량 초과 시 Redis DECR 로 롤백
     * <p>
     * 4. Kafka 기반 비동기 처리
     * - 발급은 Redis 기준으로 선검증 → Kafka 메시지 전송 → Consumer가 DB 저장
     * - RDB 부하 감소
     * <p>
     * 5. 실패 처리: 예외 발생 시 FailedIssuedCoupon 엔티티에 실패 기록 저장
     * - 향후 배치 프로그램을 통해 실패 건 재처리 또는 알림 시스템과 연계 가능
     */
    @Transactional(readOnly = true)
    public CouponIssueResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();
        final String lockKey = generateLockKey(couponId, userId);

        // Redis 기반 분산 락 (SETNX) - 사용자 단위 중복 발급 방지 목적
        boolean locked = couponCountRedisRepository.tryLock(lockKey, 5);
        if (!locked) {
            throw new InvalidIssuedCouponException("현재 쿠폰 발급 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            final Coupon coupon = loadCouponWithCaching(couponId);
            coupon.validateIssuable(LocalDateTime.now());

            CouponIssueResult duplicateResult = validateDuplicateIssue(couponId, userId);
            if (duplicateResult != null) {
                return duplicateResult;
            }

            CouponIssueResult quantityLimitResult = validateQuantityLimit(couponId, userId, coupon.getTotalQuantity());
            if (quantityLimitResult != null) {
                return quantityLimitResult;
            }

            // [이전 - 동기식 처리] Kafka 도입 전에는 아래 로직으로 발급 즉시 DB에 저장했습니다.
//            final IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
//            issuedCouponRepository.save(issuedCoupon);
//            return CouponIssueResult.from(issuedCoupon);
            // [현재 - 비동기 처리]
            couponIssueProducer.issue(userId, couponId);
            return CouponIssueResult.success(userId, couponId);

        } catch (Exception e) {
            // 발급 실패 로그 기록 및 실패 이력 저장
            // 향후 배치 프로그램 또는 알림 시스템을 통해 실패 건 재처리 또는 관리자에게 알림 가능
            log.error("failed to issue coupon - userId: {}, couponId: {}", userId, couponId, e);
            failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));
            return CouponIssueResult.failure(userId, couponId);

        } finally {
            couponCountRedisRepository.releaseLock(lockKey);
        }
    }

    private String generateLockKey(final UUID couponId, final UUID userId) {
        return "lock:coupon:" + couponId + ":user:" + userId;
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

    /**
     * Redis 기반 사용자 중복 발급 방지 (최초 요청만 true 반환)
     */
    private CouponIssueResult validateDuplicateIssue(final UUID couponId, final UUID userId) {
//        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
//            return CouponIssueResult.alreadyIssued(userId, couponId);
//        }
        boolean isFirstIssue = appliedUserRepository.add(couponId, userId);
        if (!isFirstIssue) {
            log.info("중복 발급 요청 감지 - userId: {}, couponId: {}", userId, couponId);
            return CouponIssueResult.alreadyIssued(userId, couponId);
        }
        return null;
    }

    /**
     * 기존 DB 기반 발급 수량 계산 대신
     * Redis 기반 발급 수량 제어 (key: coupon_count:{couponId})
     */
    private CouponIssueResult validateQuantityLimit(final UUID couponId, final UUID userId, final int totalQuantity) {
//        int issuedCount = issuedCouponRepository.countByCouponId(couponId);
        String countKey = "coupon_count:" + couponId;
        Long issuedCount = couponCountRedisRepository.increment(countKey);
        if (issuedCount > totalQuantity) {
            couponCountRedisRepository.decrement(countKey);
            log.info("쿠폰 수량 초과 - userId: {}, couponId: {}", userId, couponId);
            return CouponIssueResult.quantityExceeded(userId, couponId);
        }
        return null;
    }
}
