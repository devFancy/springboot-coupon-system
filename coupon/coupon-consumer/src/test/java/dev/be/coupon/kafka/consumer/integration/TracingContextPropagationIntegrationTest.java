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

/**
 * Producer에서 발행한 Kafka 메시지가 Consumer에 도달하기까지
 * MDC의 Trace 정보(GlobalTraceId, TraceId, SpanId)가 올바르게 전파되는지 검증하는 통합 테스트.
 * <p>
 * 본 테스트는 HTTP 요청을 거치지 않고 서비스 계층을 직접 호출하므로,
 * 실제 환경에서 Filter가 설정하는 TraceId와 SpanId를 테스트 코드 내에서 직접 생성하여 MDC에 설정합니다.
 */
@SpringBootTest(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"coupon_issue"})
@ActiveProfiles("test")
public class TracingContextPropagationIntegrationTest {

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
        // Filter 역할을 시뮬레이션하기 위한 MDC 컨텍스트 설정
        final String globalTraceID = UUID.randomUUID().toString();
        final String traceId = UUID.randomUUID().toString().substring(0, 32);
        final String spanId = traceId.substring(0, 16);

        MDC.put("globalTraceId", globalTraceID);
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

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
            MDC.remove("traceId");
            MDC.remove("spanId");
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
