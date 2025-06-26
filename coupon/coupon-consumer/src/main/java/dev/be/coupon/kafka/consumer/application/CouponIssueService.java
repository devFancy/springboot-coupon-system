package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponIssueService {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueFailureRecorder couponIssueFailureRecorder;
    private final Logger log = LoggerFactory.getLogger(CouponIssueService.class);

    public CouponIssueService(final IssuedCouponRepository issuedCouponRepository,
                              final CouponIssueFailureRecorder couponIssueFailureRecorder) {
        this.issuedCouponRepository = issuedCouponRepository;
        this.couponIssueFailureRecorder = couponIssueFailureRecorder;
    }

    /**
     * 쿠폰 발급 비즈니스 로직과 트랜잭션을 담당합니다.
     * Consumer로부터 메시지를 받아 실제 DB 작업을 수행합니다.
     */
    @Transactional
    public void issue(CouponIssueMessage message) {
        log.debug("쿠폰 발급 서비스 로직 시작: {}", message);
        try {
            // 1. 이미 발급된 쿠폰인지 확인합니다.
            if (issuedCouponRepository.existsByUserIdAndCouponId(message.userId(), message.couponId())) {
                log.info("이미 발급된 쿠폰입니다 - userId: {}, couponId: {}", message.userId(), message.couponId());
                return;
            }

            // 2. 쿠폰 발급 내역을 DB에 저장합니다.
            IssuedCoupon issuedCoupon = new IssuedCoupon(message.userId(), message.couponId());
            issuedCouponRepository.save(issuedCoupon);
            log.info("쿠폰 발급 완료: {}", issuedCoupon);

        } catch (Exception e) {
            // 3. DB 저장 중 예외 발생 시, 실패 이력을 기록합니다.
            log.error("쿠폰 발급 실패 - userId: {}, couponId: {}, reason={}", message.userId(), message.couponId(), e.getMessage(), e);
            couponIssueFailureRecorder.record(message.userId(), message.couponId());
            // 이 서비스에서는 예외를 잡고 실패 이력을 기록하는 것으로 책임을 다합니다.
        }
    }
}
