package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueSuccessTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @AfterEach
    void tearDown() {
        issuedCouponJpaRepository.deleteAllInBatch();
        couponJpaRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("쿠폰 발급 로직이 정상 처리되면 발급된 쿠폰 테이블에 저장된다")
    void issue_whenRequestIsValid_thenCouponIsSaved() {
        // given
        Coupon coupon = new Coupon(
                "치킨 쿠폰",
                CouponType.CHICKEN,
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10)
        );
        couponJpaRepository.save(coupon);
        final UUID couponId = coupon.getId();
        final UUID userId = UUID.randomUUID();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        couponIssueService.issue(message);

        // then
        boolean isIssued = issuedCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
        assertThat(isIssued).isTrue();
    }
}
