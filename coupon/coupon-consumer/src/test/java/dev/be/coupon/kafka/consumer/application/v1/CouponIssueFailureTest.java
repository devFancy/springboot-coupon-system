package dev.be.coupon.kafka.consumer.application.v1;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.infra.jpa.FailedIssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class CouponIssueFailureTest {

    @Autowired
    private CouponIssueServiceImpl couponIssueServiceImpl;

    @MockBean
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private FailedIssuedCouponJpaRepository failedIssuedCouponRepository;

    @AfterEach
    void tearDown() {
        failedIssuedCouponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("쿠폰 발급 DB 저장 실패 시, 실패 이력 테이블에 정상적으로 기록된다")
    void when_coupon_issue_fail_then_failedCoupon_saved() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        when(issuedCouponRepository.existsByUserIdAndCouponId(any(), any())).thenReturn(false);
        doThrow(new RuntimeException("DB 저장 실패! 테스트용 예외"))
                .when(issuedCouponRepository).save(any());

        couponIssueServiceImpl.issue(message);

        // then
        List<FailedIssuedCoupon> failedIssuedCoupons = failedIssuedCouponRepository.findAllByIsResolvedFalse();
        assertThat(failedIssuedCoupons).hasSize(1);
        assertThat(failedIssuedCoupons.get(0).getUserId()).isEqualTo(userId);
    }
}
