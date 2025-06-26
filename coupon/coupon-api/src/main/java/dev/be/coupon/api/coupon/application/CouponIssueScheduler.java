package dev.be.coupon.api.coupon.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.CouponWaitingQueueRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 주기적으로 대기열을 확인하고 Kafka로 메시지를 발행하는 스케줄러입니다.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CouponIssueScheduler {
    private final CouponWaitingQueueRepository waitingQueueRepository;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponRepository couponRepository;
    private final Logger log = LoggerFactory.getLogger(CouponIssueScheduler.class);

    @Value("${app.scheduler.batch-size:100}")
    private long batchSize;

    private final AtomicLong waitingQueueSize = new AtomicLong(0);

    public CouponIssueScheduler(CouponWaitingQueueRepository waitingQueueRepository,
                                CouponIssueProducer couponIssueProducer,
                                CouponRepository couponRepository,
                                MeterRegistry meterRegistry) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.couponIssueProducer = couponIssueProducer;
        this.couponRepository = couponRepository;

        Gauge.builder("coupon_waiting_queue_size", waitingQueueSize, AtomicLong::get)
                .description("Total size of all active coupon issue waiting queues in Redis")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 1000)
    public void processWaitingQueue() {
        log.debug("대기열 처리 스케줄러 시작 (batch-size: {})", batchSize);
        LocalDateTime now = LocalDateTime.now();

        // 1. 현재 발급 가능한 모든 'ACTIVE' 상태의 쿠폰 목록을 DB에서 조회합니다.
        List<Coupon> activeCoupons = couponRepository.findAvailableCoupons(CouponStatus.ACTIVE, now);

        updateTotalWaitingQueueSize(activeCoupons);

        if (activeCoupons.isEmpty()) {
            return;
        }

        log.info("[Scheduler] {}개의 활성 쿠폰 이벤트에 대한 대기열 처리를 시작합니다.", activeCoupons.size());

        // 2. 각 활성 쿠폰에 대해 대기열 처리 로직을 실행합니다.
        for (Coupon coupon : activeCoupons) {
            processQueueForCoupon(coupon.getId());
        }
    }

    /**
     * [신규] 모든 활성 쿠폰의 대기열 크기 합계를 조회하여 Gauge가 참조하는 변수를 업데이트합니다.
     * @param activeCoupons 현재 활성화된 쿠폰 목록
     */
    private void updateTotalWaitingQueueSize(List<Coupon> activeCoupons) {
        long totalSize = 0L;
        if (!activeCoupons.isEmpty()) {
            for (Coupon coupon : activeCoupons) {
                Long size = waitingQueueRepository.getQueueSize(coupon.getId());
                if (size != null) {
                    totalSize += size;
                }
            }
        }
        // AtomicLong 변수에 실제 대기열 크기 합계를 설정합니다.
        this.waitingQueueSize.set(totalSize);
        log.debug("Total waiting queue size updated: {}", totalSize);
    }

    /**
     * 특정 쿠폰 ID에 대한 대기열 처리 로직을 수행하는 private 메서드
     * @param couponId 처리할 쿠폰의 ID
     */
    private void processQueueForCoupon(UUID couponId) {
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
