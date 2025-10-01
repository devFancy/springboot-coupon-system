package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.infra.redis.aop.DistributedLock;
import dev.be.coupon.kafka.consumer.application.exception.CouponNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponIssuanceServiceImpl implements CouponIssuanceService {

    private final CouponRepository couponRepository;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final IssuedCouponSaver issuedCouponSaver;
    private final Logger log = LoggerFactory.getLogger(CouponIssuanceServiceImpl.class);

    public CouponIssuanceServiceImpl(final CouponRepository couponRepository,
                                     final CouponIssueFailureRecorder couponIssueFailureRecorder,
                                     final IssuedCouponSaver issuedCouponSaver) {
        this.couponRepository = couponRepository;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
        this.issuedCouponSaver = issuedCouponSaver;
    }

    /**
     * 목적: Kafka의 재처리(Retry)나 리밸런싱(Rebalancing)으로 인해
     * '동일한 당첨 메시지'가 두 번 이상 소비되는 것을 막고, DB에 쿠폰이 두 번 저장되지 않도록 보장
     * DB에 쿠폰을 저장하는 구간만 분산 락으로 보호하여 동시성을 제어합니다.
     * 순서: Lock -> Transaction -> Unlock
     <pr>
     * 발급 처리 도중 예외가 발생할 경우 실패 이력을 저장한 뒤 예외를 던져 재처리 대상에 포함시킵니다.
     * 저장된 실패 이력은 coupon-api 모듈의 스케줄러(FailedCouponIssueRetryScheduler)를 통해 재처리됩니다.
     */
    @Override
    @DistributedLock(key = "'coupon:' + #message.couponId", waitTime = 5, leaseTime = 10)
    public void process(final CouponIssueMessage message) {
        final UUID userId = message.userId();
        final UUID couponId = message.couponId();

        log.info("쿠폰 발급 처리 시작 - userId: {}, couponId: {}", userId, couponId);

        try {
            final Coupon coupon = findCouponById(couponId);
            coupon.validateIssuableStatus(LocalDateTime.now());
            issuedCouponSaver.save(userId, couponId);
        } catch (Exception e) {
            log.error("쿠폰 발급 처리 중 예상치 못한 오류 발생 - userId: {}, couponId: {}", userId, couponId, e);
            couponIssueFailureRecorder.record(userId, couponId);
            throw e;
        }
    }

    /**
     * DB에서 직접 쿠폰 정보를 조회하는 메서드
     */
    private Coupon findCouponById(final UUID couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다. ID: " + couponId));
    }
}
