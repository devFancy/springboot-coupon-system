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
import dev.be.coupon.domain.coupon.exception.CouponDomainException;
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

import static java.util.function.Function.identity;


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

    public CouponCreateResult create(final CouponCreateCommand command) {
        if (!authService.isAdmin(command.userId())) {
            throw new AuthException(ErrorType.AUTH_ADMIN_ONLY);
        }
        final Coupon coupon = new Coupon(command.couponName(), command.couponType(), command.couponDiscountType(), command.couponDiscountValue(), command.totalQuantity(), command.expiredAt());
        final Coupon savedCoupon = couponRepository.save(coupon);

        return CouponCreateResult.from(savedCoupon);
    }


    // 중복 발급 방지 -> 선착순 보장 -> 카프카
    public CouponIssueRequestResult issue(final CouponIssueCommand command) {
        final UUID couponId = command.couponId();
        final UUID userId = command.userId();
        final int totalQuantity = getTotalQuantityWithCaching(couponId);

        // 1. 중복 발급 방지 (Redis Set 활용)
        Boolean isFirstUser = couponRedisDuplicateValidate.isFirstUser(couponId, userId);
        if (!isFirstUser) {
            log.info("[COUPON_ISSUE_DUPLICATE] 중복 발급 요청으로 인해 거절되었습니다. userId: {}, couponId: {}", userId, couponId);
            return CouponIssueRequestResult.DUPLICATE;
        }

        // 2. 선착순 보장 (Redis INCR 활용)
        long entryOrder = couponEntryRedisCounter.increment(couponId);
        if (entryOrder > totalQuantity) {
            log.info("[COUPON_ISSUE_SOLD_OUT] 선착순 쿠폰 이벤트가 마감되었습니다. userId: {}, couponId: {}, order: {}/{}", userId, couponId, entryOrder, totalQuantity);
            couponRedisDuplicateValidate.remove(couponId, userId);
            return CouponIssueRequestResult.SOLD_OUT;
        }

        // 3. 선착순 성공 시, Kafka 프로듀서에 즉시 메시지 발행
        couponIssueProducer.issue(userId, couponId);
        log.info("[COUPON_ISSUE_REQUESTED] 선착순 진입 성공 및 발급 메시지를 발행했습니다. userId: {}, couponId: {}, entryOrder: {}", userId, couponId, entryOrder);
        return CouponIssueRequestResult.SUCCESS;
    }

    private int getTotalQuantityWithCaching(final UUID couponId) {
        Integer totalQuantity = couponRedisCache.getTotalQuantityById(couponId);
        if (totalQuantity != null) {
            return totalQuantity;
        }

        log.info("[COUPON_CACHE_MISS] 쿠폰 메타 정보를 DB에서 조회합니다. couponId: {}", couponId);
        Coupon couponFromDb = couponRepository.findById(couponId).orElseThrow(() -> new CouponException(ErrorType.COUPON_NOT_FOUND));

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
                .collect(Collectors.toMap(Coupon::getId, identity()));

        return issuedCoupons.stream()
                .map(issuedCoupon -> {
                    Coupon coupon = couponMap.get(issuedCoupon.getCouponId());
                    if (coupon == null) {
                        log.warn("[OWNED_COUPONS] 발급된 쿠폰(ID:{})의 원본 쿠폰 정의(ID:{})가 DB에 존재하지 않습니다.", issuedCoupon.getId(), issuedCoupon.getCouponId());
                        return null;
                    }
                    return OwnedCouponFindResult.of(issuedCoupon, coupon);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponUsageResult usage(final CouponUsageCommand command) {
        final UUID userId = command.userId();
        final UUID couponId = command.couponId();
        final LocalDateTime now = LocalDateTime.now();

        IssuedCoupon issuedCoupon = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId).orElseThrow(() -> {
            log.info("[COUPON_USAGE_FAIL] 발급된 쿠폰 내역을 찾을 수 없습니다. userId: {}, couponId: {}", userId, couponId);
            return new CouponException(ErrorType.ISSUED_COUPON_NOT_FOUND);
        });

        Coupon coupon = findCouponById(issuedCoupon.getCouponId());

        try {
            coupon.validateStatusIsActive(now);
        } catch (CouponDomainException e) {
            log.warn("[COUPON_USAGE_INVALID] 쿠폰이 현재 사용 가능한 상태가 아닙니다. | userId: {}, couponId: {}, status: {}",
                    userId, couponId, coupon.getCouponStatus());
            throw new CouponException(ErrorType.COUPON_NOT_ACTIVE, e.getMessage());
        }
        issuedCoupon.use(now);
        issuedCouponRepository.save(issuedCoupon);

        log.info("[COUPON_USAGE_SUCCESS] 쿠폰 사용 처리가 완료되었습니다. userId: {}, issuedCouponId: {}", userId, issuedCoupon.getId());
        return CouponUsageResult.of(userId, couponId, issuedCoupon.isUsed(), issuedCoupon.getUsedAt());
    }

    private Coupon findCouponById(final UUID couponId) {
        return couponRepository.findById(couponId).orElseThrow(() -> new CouponException(ErrorType.COUPON_NOT_FOUND));
    }
}
