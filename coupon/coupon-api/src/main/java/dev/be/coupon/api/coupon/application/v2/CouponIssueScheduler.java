package dev.be.coupon.api.coupon.application.v2;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.v2.CouponSoldOutCacheRepository;
import dev.be.coupon.infra.redis.v2.CouponWaitingQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 주기적으로 대기열을 확인하고 Kafka로 메시지를 발행하는 스케줄러입니다.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CouponIssueScheduler {
    private final CouponWaitingQueueRepository waitingQueueRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponRepository couponRepository;
    private final CouponSoldOutCacheRepository couponSoldOutCacheRepository;

    private final Logger log = LoggerFactory.getLogger(CouponIssueScheduler.class);


    @Value("${app.scheduler.batch-size:100}")
    private long batchSize;

    public CouponIssueScheduler(final CouponWaitingQueueRepository waitingQueueRepository,
                                final CouponIssueProducer couponIssueProducer,
                                final CouponRepository couponRepository,
                                final CouponSoldOutCacheRepository couponSoldOutCacheRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.couponRepository = couponRepository;
        this.couponSoldOutCacheRepository = couponSoldOutCacheRepository;
    }

    @Scheduled(fixedRate = 3000)
    public void issueCoupons() {
        log.debug("대기열 처리 스케줄러 시작 (batch-size: {})", batchSize);
        LocalDateTime now = LocalDateTime.now();

        List<Coupon> activeCoupons = couponRepository.findAvailableCoupons(CouponStatus.ACTIVE, now);

        if (activeCoupons.isEmpty()) {
            return;
        }

        log.info("[Scheduler] {}개의 활성 쿠폰 이벤트에 대한 대기열 처리를 시작합니다.", activeCoupons.size());

        for (Coupon coupon : activeCoupons) {
            if (couponSoldOutCacheRepository.isSoldOut(coupon.getId())) {
                log.info("[Scheduler] ===== 선착순 이벤트가 종료되었습니다. =====");
                continue;
            }
            processQueueForCoupon(coupon.getId());
        }
    }

    /**
     * 특정 쿠폰 ID에 대한 대기열 처리 로직을 수행하는 private 메서드
     *
     * @param couponId 처리할 쿠폰의 ID
     */
    private void processQueueForCoupon(final UUID couponId) {
        // 3. 해당 쿠폰의 대기열에서 처리할 사용자 조회
        Set<String> userIds = waitingQueueRepository.getWaitingUsers(couponId, batchSize);

        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        log.info("[Scheduler] 쿠폰 ID: {}에 대해 {}명의 사용자를 처리 시작.", couponId, userIds.size());

        // 4. Kafka 토픽으로 메시지 발행
        for (String userIdStr : userIds) {
            try {
                UUID userId = UUID.fromString(userIdStr);
                couponIssueProducer.issue(userId, couponId);
            } catch (Exception e) {
                log.error("[Scheduler] Kafka 발행 실패 - couponId: {}, userId: {}", couponId, userIdStr, e);
            }
        }

        // 5. 처리 시작한 사용자들을 대기열에서 제거
        waitingQueueRepository.remove(couponId, userIds);
        log.info("[Scheduler] 쿠폰 ID: {}에 대해 {}명의 사용자를 대기열에서 제거 완료.", couponId, userIds.size());
    }
}
