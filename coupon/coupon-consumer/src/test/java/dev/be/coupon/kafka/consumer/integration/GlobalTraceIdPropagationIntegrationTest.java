package dev.be.coupon.kafka.consumer.integration;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"coupon_issue"})
@ActiveProfiles("test")
public class GlobalTraceIdPropagationIntegrationTest {

    @Autowired
    private CouponIssueProducer couponIssueProducer;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @AfterEach
    void tearDown() {
        issuedCouponJpaRepository.deleteAllInBatch();
        couponJpaRepository.deleteAllInBatch();
    }

    @DisplayName("쿠폰 발급 요청시, Producer의 GlobalTrace ID가 Consumer의 비즈니스 로직까지 전파된다.")
    @Test
    void global_traceId_is_propagated_through_kafka() {
        // given
        final String globalTraceID = UUID.randomUUID().toString();
        MDC.put("globalTraceId", globalTraceID);

        try {
            Coupon coupon = createCoupon();
            UUID userId = UUID.randomUUID();

            // when
            couponIssueProducer.issue(userId, coupon.getId());


            // then
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean isIssued = issuedCouponJpaRepository.findAll().stream()
                        .anyMatch(ic -> ic.getUserId().equals(userId) && ic.getCouponId().equals(coupon.getId()));
                assertThat(isIssued).isTrue();
            });

        } finally {
            MDC.remove("globalTraceId");
        }
    }

    private Coupon createCoupon() {
        Coupon coupon = new Coupon(
                "선착순 쿠폰",
                CouponType.BURGER,
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10)
        );

        return couponJpaRepository.save(coupon);
    }
}
