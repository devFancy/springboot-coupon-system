package dev.be.coupon.api.coupon.application;

import deb.be.coupon.CouponStatus;
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
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;
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
     * 쿠폰 발급 시 핵심 검증 포인트
     * <p>
     * 1. 사용자별 중복 발급 방지 - Redis Set 기반 제어 (key: applied_user:{couponId})
     * - 최초 요청만 true, 이후는 중복으로 간주
     * - 중복이면 발급 수량 증가를 수행하지 않음 (정확한 수량 보장 목적)
     * <p>
     * 2. 전체 발급 수량 제한 - Redis INCR 기반 제어 (key: coupon_count:{couponId})
     * - 최초 요청인 경우에만 수량 증가
     * - 초과 시 Redis 값 롤백 (decrement)
     * <p>
     * 3. Kafka 기반 비동기 처리
     * - 발급은 Redis 기준으로 선검증 → Kafka 메시지 전송 → Consumer가 DB 저장
     * - RDB 부하는 줄이고, 발급 속도는 향상됨
     * <p>
     * 캐시된 쿠폰 정보를 사용해 RDB 조회 부하도 최소화함
     */
    @Transactional(readOnly = true)
    public CouponIssueResult issue(final CouponIssueCommand command) {
        final UUID userId = command.userId();
        final UUID couponId = command.couponId();

        // Redis 기반 분산 락 (SETNX) - 사용자 단위 중복 발급 방지 목적
        final String lockKey = "lock:coupon:" + couponId + ":user:" + userId;
        boolean locked = couponCountRedisRepository.tryLock(lockKey, 5);

        if (!locked) {
            throw new InvalidIssuedCouponException("현재 쿠폰 발급 처리 중입니다. 잠시 후 다시 시도해주세요."); // 이미 누가 처리 중
        }

        try {

            final Coupon coupon = couponCacheRepository.findById(couponId)
                    .orElseGet(() -> {
                        Coupon fromDb = couponRepository.findById(couponId)
                                .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));
                        couponCacheRepository.save(fromDb);
                        return fromDb;
                    });

            coupon.updateStatusBasedOnDate(LocalDateTime.now());
            if (coupon.getCouponStatus() != CouponStatus.ACTIVE) {
                throw new InvalidCouponException("현재 쿠폰은 발급 가능한 상태가 아닙니다.");
            }

//            if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
//                throw new InvalidIssuedCouponException("이미 해당 쿠폰을 발급받았습니다.");
//            }
            // Redis 기반 사용자 중복 발급 방지 (최초 요청만 true 반환)
            boolean isFirstIssue = appliedUserRepository.add(couponId, userId);
            if (!isFirstIssue) {
                throw new InvalidIssuedCouponException("이미 해당 쿠폰을 발급받았습니다.");
            }

            //int issuedCount = issuedCouponRepository.countByCouponId(couponId);
            // 기존 DB 기반 발급 수량 계산 대신
            // Redis 기반 발급 수량 제어 (key: coupon_count:{couponId})
            String countKey = "coupon_count:" + couponId;
            Long issuedCount = couponCountRedisRepository.increment(countKey);
            if (issuedCount > coupon.getTotalQuantity()) {
                couponCountRedisRepository.decrement(countKey);
                appliedUserRepository.remove(couponId, userId);
                throw new InvalidIssuedCouponException("해당 쿠폰의 발급 수량이 초과되었습니다.");
            }

            // [이전 - 동기식 처리] Kafka 도입 전에는 아래 로직으로 발급 즉시 DB에 저장했습니다.
//            final IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
//            issuedCouponRepository.save(issuedCoupon);
//            return CouponIssueResult.from(issuedCoupon);
            // [현재 - 비동기 처리]
            couponIssueProducer.issue(userId, couponId);
            return CouponIssueResult.success(userId, couponId);

        } catch (Exception e) {
            log.error("failed to issue coupon - userId: {}, couponId: {}", userId, couponId, e);
            failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));
            return CouponIssueResult.failure(userId, couponId);

        } finally {
            couponCountRedisRepository.releaseLock(lockKey);
        }
    }
}
