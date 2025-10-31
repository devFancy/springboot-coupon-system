package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.FailedIssuedCouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ActiveProfiles("test")
@SpringBootTest
class CouponIssueConsumerTest {

    @Value("${kafka.topic.coupon-issue}")
    private String issueTopic;

    @Value("${kafka.topic.coupon-issue-retry}")
    private String retryTopic;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private FailedIssuedCouponJpaRepository failedIssuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        failedIssuedCouponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("[신규 - 성공] 신규 쿠폰 발급 메시지를 받으면 DB에 쿠폰이 정상적으로 저장된다.")
    void success_save_issued_coupon_when_receive_new_issue_message() {
        // given
        final int messageCount = 100;
        final Coupon coupon = createCoupon(100);
        List<CouponIssueMessage> messages = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            messages.add(new CouponIssueMessage(UUID.randomUUID(), coupon.getId(), null));
        }

        // when
        messages.forEach(message -> kafkaTemplate.send(issueTopic, message));

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int finalIssuedCount = issuedCouponRepository.countByCouponId(coupon.getId());
            assertThat(finalIssuedCount).isEqualTo(messageCount);
        });
    }

    @Test
    @DisplayName("[재처리 - 성공] 재처리 메시지를 받으면 쿠폰을 저장하고, 실패 이력을 '해결됨'으로 변경한다.")
    void success_reissue_coupon_and_resolve_failure_when_receive_retry_message() {
        // given
        final Coupon coupon = createCoupon(1);
        final UUID userId = UUID.randomUUID();
        final UUID couponId = coupon.getId();
        FailedIssuedCoupon failedIssuedCoupon = failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));

        CouponIssueMessage message = new CouponIssueMessage(userId, couponId, failedIssuedCoupon.getId());

        // when
        kafkaTemplate.send(retryTopic, message);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
            assertThat(isIssued).isTrue();

            FailedIssuedCoupon result = failedIssuedCouponRepository.findById(failedIssuedCoupon.getId()).orElseThrow();
            assertThat(result.isResolved()).isTrue();
        });
    }

    private Coupon createCoupon(int totalQuantity) {
        Coupon coupon = new Coupon(
                "선착순 쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED,
                BigDecimal.valueOf(10_000L),
                totalQuantity,
                LocalDateTime.now().plusDays(7)
        );
        return couponRepository.save(coupon);
    }
}
