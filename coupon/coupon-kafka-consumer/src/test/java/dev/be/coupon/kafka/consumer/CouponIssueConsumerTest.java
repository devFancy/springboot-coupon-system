package dev.be.coupon.kafka.consumer;

import dev.be.coupon.kafka.consumer.domain.FailedIssuedCouponRepository;
import dev.be.coupon.kafka.consumer.domain.IssuedCouponRepository;
import dev.be.coupon.kafka.consumer.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueConsumerTest {

    @Autowired
    private KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private FailedIssuedCouponRepository failedIssuedCouponRepository;

    @Test
    @DisplayName("쿠폰 발급 메시지를 정상 처리하면 발급된 쿠폰 테이블에 저장된다")
    void success_save_issued_coupon_when_kafka_message_processed_successfully() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        kafkaTemplate.send("coupon_issue", message).get();

        // then
        Thread.sleep(5000);
        long issuedCount = countIssuedCoupons(couponId);
        assertThat(issuedCount).isEqualTo(1);
    }

    private long countIssuedCoupons(final UUID couponId) {
        return issuedCouponRepository.countByCouponId(couponId);
    }
}
