package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ActiveProfiles("test")
@SpringBootTest
class CouponIssuanceServiceImplTest {

    @Value("${kafka.topic.coupon-issue}")
    private String topic;

    @Autowired
    private CouponIssuanceServiceImpl couponIssuanceService;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("쿠폰 발급 메시지를 받으면 DB에 정상적으로 저장된다.")
    void success_save_issued_coupon_to_db() {
        // given
        final UUID couponId = createCoupon(100).getId();
        final UUID userId = UUID.randomUUID();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        couponIssuanceService.process(message);

        // then
        boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
        assertThat(isIssued).isTrue();
    }

    @Test
    @DisplayName("유효한 쿠폰 발급 메시지를 수신하면, DB에 정확한 갯수로 저장된다.")
    void success_save_all_issued_coupons_to_db() {
        // given
        final Coupon coupon = createCoupon(100);
        final UUID couponId = coupon.getId();

        // given
        final int messageCount = 100;
        List<CouponIssueMessage> messages = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            messages.add(new CouponIssueMessage(UUID.randomUUID(), couponId));
        }

        // when
        messages.forEach(message -> kafkaTemplate.send(topic, message));

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long finalIssuedCount = issuedCouponRepository.countByCouponId(couponId);
            assertThat(finalIssuedCount).isEqualTo(messageCount);
        });
    }

    private Coupon createCoupon(int totalQuantity) {
        Coupon coupon = new Coupon(
                "선착순 쿠폰",
                CouponType.CHICKEN,
                totalQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10)
        );
        return couponRepository.save(coupon);
    }
}
