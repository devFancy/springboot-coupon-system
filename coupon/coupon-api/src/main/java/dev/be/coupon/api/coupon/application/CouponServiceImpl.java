package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.auth.application.AuthService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.exception.CouponNotFoundException;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.CouponEntryRedisCounter;
import dev.be.coupon.infra.redis.CouponRedisCache;
import dev.be.coupon.infra.redis.CouponRedisWaitingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;


@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRedisWaitingQueue waitingQueueRepository;
    private final CouponRedisCache couponRedisCache;
    private final CouponEntryRedisCounter couponEntryRedisCounter;
    private final CouponIssueProducer couponIssueProducer;
    private final AuthService authService;

    private final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);


    public CouponServiceImpl(final CouponRepository couponRepository,
                             final IssuedCouponRepository issuedCouponRepository,
                             final CouponRedisWaitingQueue waitingQueueRepository,
                             final CouponRedisCache couponRedisCache,
                             final CouponEntryRedisCounter couponEntryRedisCounter,
                             final CouponIssueProducer couponIssueProducer,
                             final AuthService authService) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.waitingQueueRepository = waitingQueueRepository;
        this.couponRedisCache = couponRedisCache;
        this.couponEntryRedisCounter = couponEntryRedisCounter;
        this.couponIssueProducer = couponIssueProducer;
        this.authService = authService;
    }

    public CouponCreateResult create(
            final CouponCreateCommand command) {

        if (!authService.isAdmin(command.userId())) {
            throw new UnauthorizedAccessException("쿠폰 생성은 관리자만 가능합니다.");
        }

        final Coupon coupon = command.toDomain();
        return CouponCreateResult.from(couponRepository.save(coupon));
    }


    // 입장 통제(INCR) -> 대기(Sorted set) -> 카프카 프로듀서
    public CouponIssueRequestResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();
        final int totalQuantity = getTotalQuantityWithCaching(couponId);

        // 1. 중복 참여 검증 (Redis Set 활용)
        Boolean isFirstEntryForUser = waitingQueueRepository.add(couponId, userId);
        if (!isFirstEntryForUser) {
            log.info("중복 참여 요청 - userId: {}", userId);
            return CouponIssueRequestResult.DUPLICATE;
        }

        // 2. 선착순 마감 여부 판별 (Redis INCR 활용)
        long entryOrder = couponEntryRedisCounter.increment(couponId);
        if (entryOrder > totalQuantity) {
            log.warn("선착순 마감(절대 순번 기준) - userId: {}, entryOrder: {}, totalQuantity: {}", userId, entryOrder, totalQuantity);

            waitingQueueRepository.remove(couponId, Set.of(userId.toString()));
            return CouponIssueRequestResult.SOLD_OUT;
        }

        // 3. 선착순 성공 시, Kafka 프로듀서에 즉시 메시지 발행
        couponIssueProducer.issue(userId, couponId);
        log.info("쿠폰 발급 요청 성공. Kafka 발행 완료. userId: {}, entryOrder: {}", userId, entryOrder);

        return CouponIssueRequestResult.SUCCESS;
    }

    // 캐시를 먼저 확인하여 불필요한 DB 조회를 막습니다.
    private int getTotalQuantityWithCaching(final UUID couponId) {
        Integer totalQuantity = couponRedisCache.getTotalQuantityById(couponId);
        if (totalQuantity != null) {
            return totalQuantity;
        }

        log.info("캐시 미스 발생. DB에서 쿠폰 정보를 조회합니다. couponId: {}", couponId);
        Coupon couponFromDb = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("ID: " + couponId + "에 해당하는 쿠폰을 찾을 수 없습니다."));

        couponRedisCache.save(couponFromDb);
        return couponFromDb.getTotalQuantity();
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


    private Coupon loadCouponWithCaching(final UUID couponId) {
        return couponRedisCache.getCouponById(couponId)
                .orElseGet(() -> {
                    Coupon fromDb = couponRepository.findById(couponId)
                            .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));
                    couponRedisCache.save(fromDb);
                    return fromDb;
                });
    }
}
