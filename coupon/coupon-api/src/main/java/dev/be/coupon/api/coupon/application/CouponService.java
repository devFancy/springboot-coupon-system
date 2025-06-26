package dev.be.coupon.api.coupon.application;

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
import dev.be.coupon.infra.redis.AppliedUserRepository;
import dev.be.coupon.infra.redis.CouponCacheRepository;
import dev.be.coupon.infra.redis.CouponCountRedisRepository;
import dev.be.coupon.infra.redis.CouponWaitingQueueRepository;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final UserRoleChecker userRoleChecker;
    private final CouponWaitingQueueRepository waitingQueueRepository;

    private final Logger log = LoggerFactory.getLogger(CouponService.class);

    public CouponService(final CouponRepository couponRepository,
                         final IssuedCouponRepository issuedCouponRepository,
                         final UserRoleChecker userRoleChecker,
                         final CouponWaitingQueueRepository waitingQueueRepository) {
        this.couponRepository = couponRepository;
        this.issuedCouponRepository = issuedCouponRepository;
        this.userRoleChecker = userRoleChecker;
        this.waitingQueueRepository = waitingQueueRepository;
    }

    public CouponCreateResult create(
            final CouponCreateCommand command) {

        if (!userRoleChecker.isAdmin(command.userId())) {
            throw new UnauthorizedAccessException("쿠폰 생성은 관리자만 가능합니다.");
        }

        final Coupon coupon = command.toDomain();
        return CouponCreateResult.from(couponRepository.save(coupon));
    }

    public void requestIssue(final CouponIssueCommand command) {
        waitingQueueRepository.add(command.couponId(), command.userId());
        log.info("[API] 쿠폰 발급 요청을 대기열에 추가했습니다 - userId: {}", command.userId());
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
