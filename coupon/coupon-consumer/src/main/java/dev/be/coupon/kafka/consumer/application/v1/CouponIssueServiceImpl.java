package dev.be.coupon.kafka.consumer.application.v1;

import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.application.CouponIssueFailureRecorder;
import dev.be.coupon.kafka.consumer.application.CouponIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("couponIssueV1Service")
public class CouponIssueServiceImpl implements CouponIssueService {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final Logger log = LoggerFactory.getLogger(CouponIssueServiceImpl.class);

    public CouponIssueServiceImpl(final IssuedCouponRepository issuedCouponRepository,
                                  final CouponIssueFailureRecorder couponIssueFailureRecorder) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
    }

    /**
     * 쿠폰 발급 비즈니스 로직과 트랜잭션을 담당합니다.
     * Consumer로부터 메시지를 받아 실제 DB 작업을 수행합니다.
     */
    @Transactional
    public void issue(final CouponIssueMessage message) {
        log.debug("[Consumer-V1] 쿠폰 발급 서비스 로직 시작: {}", message);
        try {
            if (issuedCouponRepository.existsByUserIdAndCouponId(message.userId(), message.couponId())) {
                log.info("[Consumer-V1] 이미 발급된 쿠폰입니다 - userId: {}, couponId: {}", message.userId(), message.couponId());
                return;
            }

            IssuedCoupon issuedCoupon = new IssuedCoupon(message.userId(), message.couponId());
            issuedCouponRepository.save(issuedCoupon);
            log.info("[Consumer-V1] 쿠폰 발급 완료: {}", issuedCoupon);

        } catch (Exception e) {
            log.error("[Consumer-V1] 쿠폰 발급 실패 - userId: {}, couponId: {}, reason={}", message.userId(), message.couponId(), e.getMessage(), e);
            couponIssueFailureRecorder.record(message.userId(), message.couponId());
        }
    }
}
