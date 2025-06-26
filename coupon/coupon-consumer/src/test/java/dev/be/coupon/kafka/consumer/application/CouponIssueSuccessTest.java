package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueSuccessTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @AfterEach
    void tearDown() {
        issuedCouponJpaRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("쿠폰 발급 로직이 정상 처리되면 발급된 쿠폰 테이블에 저장된다")
    void issue_whenRequestIsValid_thenCouponIsSaved() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        couponIssueService.issue(message);

        // then
        boolean isIssued = issuedCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
        assertThat(isIssued).isTrue();
    }
}
