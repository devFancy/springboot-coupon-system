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
import dev.be.coupon.api.coupon.domain.IssuedCouponRepository;
import dev.be.coupon.api.coupon.domain.UserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import dev.be.coupon.api.coupon.infrastructure.CouponCountRedisRepository;
import dev.be.coupon.api.coupon.infrastructure.kafka.producer.CouponIssueProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserRoleChecker userRoleChecker;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueProducer couponIssueProducer;

    public CouponService(final CouponRepository couponRepository,
                         final UserRoleChecker userRoleChecker,
                         final IssuedCouponRepository issuedCouponRepository,
                         final CouponCountRedisRepository couponCountRedisRepository,
                         final CouponIssueProducer couponIssueProducer) {
        this.couponRepository = couponRepository;
        this.userRoleChecker = userRoleChecker;
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponCountRedisRepository = couponCountRedisRepository;
        this.couponIssueProducer = couponIssueProducer;
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
     * 1. 전체 발급 수량 제한 - Redis INCR 기반 제어 (coupon_count:{couponId})
     * 2. 사용자별 중복 발급 방지 - Redis 락 + DB 중복 확인
     * <p>
     * 발급하는 쿠폰의 수량이 많아질수록 RDB 부하가 커짐 -> Kafka 기술 사용
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

            final Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));

            coupon.updateStatusBasedOnDate(LocalDateTime.now());
            if (coupon.getCouponStatus() != CouponStatus.ACTIVE) {
                throw new InvalidCouponException("현재 쿠폰은 발급 가능한 상태가 아닙니다.");
            }

            if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new InvalidIssuedCouponException("이미 해당 쿠폰을 발급받았습니다.");
            }

            // int issuedCount = issuedCouponRepository.countByCouponId(couponId);
            // 기존 DB 기반 발급 수량 계산 대신
            // Redis 기반 발급 수량 제어 (key: coupon_count:{couponId})
            String countKey = "coupon_count:" + couponId;
            Long issuedCount = couponCountRedisRepository.increment(countKey);
            if (issuedCount > coupon.getTotalQuantity()) {
                throw new InvalidIssuedCouponException("해당 쿠폰의 발급 수량이 초과되었습니다.");
            }

            // [이전 동기 방식] Kafka 도입 전에는 아래 로직으로 발급 즉시 DB에 저장했습니다.
//            final IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
//            issuedCouponRepository.save(issuedCoupon);
//            return CouponIssueResult.from(issuedCoupon);
            couponIssueProducer.issue(userId, couponId);
            return new CouponIssueResult(userId, couponId, false, LocalDateTime.now());

        } finally {
            couponCountRedisRepository.releaseLock(lockKey);
        }
    }
}
