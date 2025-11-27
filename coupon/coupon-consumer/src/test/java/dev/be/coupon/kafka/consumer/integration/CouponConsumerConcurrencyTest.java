package dev.be.coupon.kafka.consumer.integration;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.be.coupon.domain.coupon.CouponFixtures.정상_쿠폰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@SpringBootTest
public class CouponConsumerConcurrencyTest {

    @Value("${kafka.topic.coupon-issue}")
    private String issueTopic;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100개의 쿠폰 발급 요청이 동시에 들어왔을 때, 모든 메시지를 유실 없이 처리하여 DB에 저장한다.")
    void success_issued_coupons_concurrently() throws InterruptedException {
        // given
        final int messageCount = 100;
        final Coupon coupon = createCoupon(messageCount);

        final ExecutorService executorService = Executors.newFixedThreadPool(32);
        final CountDownLatch latch = new CountDownLatch(messageCount);

        // when
        for (int i = 0; i < messageCount; i++) {
            executorService.submit(() -> {
                try {
                    UUID userId = UUID.randomUUID();
                    kafkaTemplate.send(issueTopic, new CouponIssueMessage(userId, coupon.getId(), null));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long finalIssuedCount = issuedCouponRepository.countByCouponId(coupon.getId());
            assertThat(finalIssuedCount).isEqualTo(messageCount);
        });

        executorService.shutdown();
    }


    private Coupon createCoupon(final int totalQuantity) {
        Coupon coupon = 정상_쿠폰(totalQuantity);
        return couponRepository.save(coupon);
    }
}
