package dev.be.coupon.api.coupon.infrastructure;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.infra.redis.CouponRedisWaitingQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CouponQueueMetrics {
    private final CouponRepository couponRepository;
    private final CouponRedisWaitingQueue waitingQueueRepository;
    private final AtomicLong waitingQueueSize = new AtomicLong(0);
    private final Logger log = LoggerFactory.getLogger(CouponQueueMetrics.class);

    public CouponQueueMetrics(final CouponRepository couponRepository,
                              final CouponRedisWaitingQueue waitingQueueRepository,
                              final MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.waitingQueueRepository = waitingQueueRepository;

        Gauge.builder("coupon.waiting.queue.size", waitingQueueSize, AtomicLong::get)
                .description("Total size of all active coupon issue waiting queues in Redis")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5000)
    public void updateTotalWaitingQueueSize() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> activeCoupons = couponRepository.findAvailableCoupons(CouponStatus.ACTIVE, now);

        long totalSize = 0;
        if (!activeCoupons.isEmpty()) {
            for (Coupon coupon : activeCoupons) {
                Long size = waitingQueueRepository.getQueueSize(coupon.getId());
                if (size != null) {
                    totalSize += size;
                }
            }
        }

        this.waitingQueueSize.set(totalSize);
        log.debug("Total waiting queue size metric updated: {}", totalSize);
    }
}
