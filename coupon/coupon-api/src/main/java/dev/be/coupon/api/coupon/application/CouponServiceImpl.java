package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.auth.application.AuthService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.dto.OwnedCouponFindResult;
import dev.be.coupon.api.support.error.AuthException;
import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.CouponEntryRedisCounter;
import dev.be.coupon.infra.redis.CouponRedisCache;
import dev.be.coupon.infra.redis.CouponRedisDuplicateValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponRedisDuplicateValidate couponRedisDuplicateValidate;
    private final CouponEntryRedisCounter couponEntryRedisCounter;
    private final CouponRedisCache couponRedisCache;
    private final CouponIssueProducer couponIssueProducer;
    private final AuthService authService;

    private final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);


    public CouponServiceImpl(final CouponRepository couponRepository,
                             final IssuedCouponRepository issuedCouponRepository,
                             final CouponRedisDuplicateValidate couponRedisDuplicateValidate,
                             final CouponEntryRedisCounter couponEntryRedisCounter,
                             final CouponRedisCache couponRedisCache,
                             final CouponIssueProducer couponIssueProducer,
                             final AuthService authService) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponRedisDuplicateValidate = couponRedisDuplicateValidate;
        this.couponEntryRedisCounter = couponEntryRedisCounter;
        this.couponRedisCache = couponRedisCache;
        this.couponIssueProducer = couponIssueProducer;
        this.authService = authService;
    }

    public CouponCreateResult create(
            final CouponCreateCommand command) {

        if (!authService.isAdmin(command.userId())) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }

        final Coupon coupon = new Coupon(
                command.couponName(),
                command.couponType(),
                command.couponDiscountType(),
                command.couponDiscountValue(),
                command.totalQuantity(),
                command.expiredAt()
        );
        final Coupon savedCoupon = couponRepository.save(coupon);
        return CouponCreateResult.from(savedCoupon);
    }


    // 중복 참여 방지 -> 선착순 보장 -> 카프카 프로듀서
    public CouponIssueRequestResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();
        final int totalQuantity = getTotalQuantityWithCaching(couponId);

        // 1. 중복 참여 방지 (Redis Set 활용)
        Boolean isFirstUser = couponRedisDuplicateValidate.isFirstUser(couponId, userId);
        if (!isFirstUser) {
            log.info("중복 참여 요청 - userId: {}", userId);
            return CouponIssueRequestResult.DUPLICATE;
        }

        // 2. 선착순 보장 (Redis INCR 활용)
        long entryOrder = couponEntryRedisCounter.increment(couponId);
        if (entryOrder > totalQuantity) {
            log.info("선착순 마감(절대 순번 기준) - userId: {}, entryOrder: {}, totalQuantity: {}", userId, entryOrder, totalQuantity);
            couponRedisDuplicateValidate.remove(couponId, userId);
            return CouponIssueRequestResult.SOLD_OUT;
        }

        // 3. 선착순 성공 시, Kafka 프로듀서에 즉시 메시지 발행
        couponIssueProducer.issue(userId, couponId);
        log.info("쿠폰 발급 요청 성공. Kafka 프로듀서에 메시지 발행 완료. userId: {}, entryOrder: {}", userId, entryOrder);

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
                .orElseThrow(() -> new CouponException(ErrorType.COUPON_NOT_FOUND));

        couponRedisCache.save(couponFromDb);
        return couponFromDb.getTotalQuantity();
    }

    @Transactional(readOnly = true)
    public List<OwnedCouponFindResult> getOwnedCoupons(final UUID userId) {
        final List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findAllByUserId(userId);
        if (issuedCoupons.isEmpty()) return Collections.emptyList();

        final Set<UUID> couponIds = issuedCoupons.stream()
                .map(IssuedCoupon::getCouponId)
                .collect(Collectors.toSet());

        final Map<UUID, Coupon> couponMap = couponRepository.findAllById(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        return issuedCoupons.stream()
                .map(issuedCoupon -> {
                    Coupon coupon = couponMap.get(issuedCoupon.getCouponId());

                    if (coupon == null) {
                        log.info("[CouponServiceImpl_getOwnedCoupons] 발급된 쿠폰(ID: {})이 있으나, 원본 쿠폰(ID: {})을 찾을 수 없습니다.",
                                issuedCoupon.getId(), issuedCoupon.getCouponId());
                        return null;
                    }

                    return new OwnedCouponFindResult(
                            issuedCoupon.getId(),
                            issuedCoupon.getUserId(),
                            issuedCoupon.isUsed(),
                            issuedCoupon.getIssuedAt(),
                            issuedCoupon.getUsedAt(),
                            coupon.getId(),
                            coupon.getCouponName().getName(),
                            coupon.getCouponType(),
                            coupon.getCouponDiscountType(),
                            coupon.getCouponDiscountValue(),
                            coupon.getCouponStatus()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponUsageResult usage(final CouponUsageCommand command) {
        final UUID userId = command.userId();
        final UUID couponId = command.couponId();
        final LocalDateTime now = LocalDateTime.now();

        IssuedCoupon issuedCoupon = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> {
                    log.info("사용자 ID:{})에게 발급된 쿠폰 정의 ID: {}이 존재하지 않습니다.", userId, couponId);
                    return new CouponException(ErrorType.ISSUED_COUPON_NOT_FOUND);
                });

        Coupon coupon = findCouponById(issuedCoupon.getCouponId());
        coupon.validateStatusIsActive(now);

        issuedCoupon.use(now);
        issuedCouponRepository.save(issuedCoupon);

        log.info("쿠폰(쿠폰 정의 ID:{}, 발급된 쿠폰 ID:{})이 사용자 ID:{}에 의해 성공적으로 사용되었습니다. 사용 시각: {}",
                couponId, issuedCoupon.getId(), userId, issuedCoupon.getUsedAt());

        return CouponUsageResult.from(userId, couponId, issuedCoupon.isUsed(), issuedCoupon.getUsedAt());
    }

    private Coupon findCouponById(final UUID couponId) {
        log.info("DB에서 쿠폰 정보를 조회합니다. couponId: {}", couponId);
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(ErrorType.COUPON_NOT_FOUND));
    }
}
