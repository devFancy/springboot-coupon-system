package dev.be.coupon.api.coupon.application.v1;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.exception.CouponIssueException;
import dev.be.coupon.api.coupon.application.exception.CouponNotFoundException;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.domain.coupon.UserRoleChecker;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.v1.AppliedUserRepository;
import dev.be.coupon.infra.redis.v1.CouponV1CacheRepository;
import dev.be.coupon.infra.redis.v1.CouponCountRedisRepository;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponServiceImplV1 implements CouponServiceV1 {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final UserRoleChecker userRoleChecker;
    private final CouponV1CacheRepository couponV1CacheRepository;
    private final CouponCountRedisRepository couponCountRedisRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final AppliedUserRepository appliedUserRepository;

    private final Logger log = LoggerFactory.getLogger(CouponServiceImplV1.class);

    public CouponServiceImplV1(final CouponRepository couponRepository,
                               final IssuedCouponRepository issuedCouponRepository,
                               final UserRoleChecker userRoleChecker,
                               final CouponV1CacheRepository couponV1CacheRepository,
                               final CouponCountRedisRepository couponCountRedisRepository,
                               final CouponIssueProducer couponIssueProducer,
                               final AppliedUserRepository appliedUserRepository) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.userRoleChecker = userRoleChecker;
        this.couponV1CacheRepository = couponV1CacheRepository;
        this.couponCountRedisRepository = couponCountRedisRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.appliedUserRepository = appliedUserRepository;
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
     * 1. 분산 락: AOP를 통해 메서드 진입 전에 획득 (키는 SpEL로 command 객체의 필드 참조)
     * - SpEL 형식 예시. Key='LOCK:coupon:{couponId}:user:{userId}'
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
     */
    // 락이 필요한 메서드에만 적용하는 것이 좋다
    @DistributedLock(key = "'coupon:' + #command.couponId() + ':user:' + #command.userId()", waitTime = 5, leaseTime = 30)
    public CouponIssueResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();

        final Coupon coupon = loadCouponWithCaching(couponId);
        coupon.validateIssuableStatus(LocalDateTime.now());

        CouponIssueResult duplicateResult = validateDuplicateIssue(couponId, userId);
        if (duplicateResult != null) {
            return duplicateResult;
        }

        CouponIssueResult quantityLimitResult = validateQuantityLimit(couponId, userId, coupon.getTotalQuantity());
        if (quantityLimitResult != null) {
            appliedUserRepository.remove(couponId, userId);
            log.info("수량 초과로 인한 중복 발급 방지 Set 롤백 - userId: {}, couponId: {}", userId, couponId);
            return quantityLimitResult;
        }

        // Kafka 메시지 발행 및 실패 시 보상 트랜잭션
        String countKey = "coupon_count:" + couponId;
        try {
            couponIssueProducer.issue(userId, couponId);
            log.info("쿠폰 발급 요청 Kafka 전송 성공 - userId: {}, couponId: {}", userId, couponId);
            log.info("쿠폰 발급 요청이 성공적으로 접수되었습니다. 잠시 후 '내 쿠폰함'에서 확인해 주세요");
            return CouponIssueResult.success(userId, couponId);
        } catch (KafkaException ke) {
            log.error("Kafka 메시지 발행 실패 - userId: {}, couponId: {}. Redis 변경사항 롤백 시도.", userId, couponId, ke);
            rollbackRedisOperations(couponId, userId, countKey);
            throw new CouponIssueException("쿠폰 발급 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            log.error("쿠폰 발급 중 예기치 않은 오류가 발생 - userId: {}, couponId: {}. Redis 변경사항 롤백 시도.", userId, couponId, e);
            rollbackRedisOperations(couponId, userId, countKey);
            throw new CouponIssueException("쿠폰 발급 처리 중 예기치 않은 내부 오류가 발생했습니다.");
        }
    }

    private Coupon loadCouponWithCaching(final UUID couponId) {
        return couponV1CacheRepository.findById(couponId)
                .orElseGet(() -> {
                    Coupon fromDb = couponRepository.findById(couponId)
                            .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));
                    couponV1CacheRepository.save(fromDb);
                    return fromDb;
                });
    }

    /**
     * Redis 기반 사용자 중복 발급 방지 (최초 요청만 true 반환)
     */
    private CouponIssueResult validateDuplicateIssue(final UUID couponId, final UUID userId) {
        boolean isFirstIssue = appliedUserRepository.add(couponId, userId);
        if (!isFirstIssue) {
            log.info("중복 발급 요청입니다. 이미 처리된 사용자입니다. - userId: {}", userId);
            return CouponIssueResult.alreadyIssued(userId, couponId);
        }
        return null;
    }

    /**
     * 기존 DB 기반 발급 수량 계산 대신
     * Redis 기반 발급 수량 제어 (key: coupon_count:{couponId})
     */
    private CouponIssueResult validateQuantityLimit(final UUID couponId, final UUID userId, final int totalQuantity) {
        String countKey = "coupon_count:" + couponId;
        Long issuedCount = couponCountRedisRepository.increment(countKey);

        if (issuedCount > totalQuantity) {
            couponCountRedisRepository.decrement(countKey);
            log.warn("[Consumer] 쿠폰 수량이 초과되었습니다. (현재 카운트: {})", issuedCount);
            return CouponIssueResult.quantityExceeded(userId, couponId);
        }
        return null;
    }

    private void rollbackRedisOperations(final UUID couponId, final UUID userId, final String countKey) {
        try {
            appliedUserRepository.remove(couponId, userId);
            couponCountRedisRepository.decrement(countKey);
            log.info("Kafka 발행 실패로 인한 Redis 변경사항 롤백 완료 - userId: {}, couponId: {}", userId, couponId);
        } catch (Exception redisEx) {
            log.error("[Consumer] Redis 롤백 중 심각한 오류 발생. 데이터 불일치 가능성이 있습니다. - userId: {}", userId, redisEx);
        }
    }

    @Transactional
    public CouponUsageResult usage(final CouponUsageCommand command) {
        final UUID userId = command.userId();
        final UUID couponId = command.couponId();
        final LocalDateTime now = LocalDateTime.now();

        IssuedCoupon issuedCoupon = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> {
                    log.warn("사용자 ID:{})에게 발급된 쿠폰 정의 ID: {}이 존재하지 않습니다.", userId, couponId);
                    return new IssuedCouponNotFoundException("발급되지 않았거나 소유하지 않은 쿠폰입니다.");
                });

        Coupon coupon = loadCouponWithCaching(issuedCoupon.getCouponId());
        coupon.validateUsableStatus(now);

        issuedCoupon.use(now);
        issuedCouponRepository.save(issuedCoupon);

        log.info("쿠폰(쿠폰 정의 ID:{}, 발급된 쿠폰 ID:{})이 사용자 ID:{}에 의해 성공적으로 사용되었습니다. 사용 시각: {}",
                couponId, issuedCoupon.getId(), userId, issuedCoupon.getUsedAt());

        return CouponUsageResult.from(userId, couponId, issuedCoupon.isUsed(), issuedCoupon.getUsedAt());
    }
}
