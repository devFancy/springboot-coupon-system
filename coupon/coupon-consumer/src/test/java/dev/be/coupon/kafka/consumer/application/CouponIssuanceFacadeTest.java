package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ActiveProfiles("test")
@SpringBootTest
class CouponIssuanceFacadeTest {

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

    @SpyBean
    private CouponIssuanceService couponIssuanceService;

    @SpyBean
    private FailedCouponRetryCountIncrease failedCouponRetryCountIncrease;

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
    @DisplayName("[신규 - 실패] 쿠폰 발급 처리 중 네트워크 오류 혹은 DB 오류가 발생하면, 실패 이력이 저장된다")
    void fail_save_failed_issued_coupon_when_db_error_occurs() {
        // given
        final Coupon coupon = createCoupon(1);
        final UUID userId = UUID.randomUUID();
        final UUID couponId = coupon.getId();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId, null);

        doThrow(new RuntimeException("Network Error or DB Connection Failed"))
                .when(couponIssuanceService).issue(userId, couponId);

        // when
        kafkaTemplate.send(issueTopic, message);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(couponIssuanceService, times(1)).recordFailure(userId, couponId);

            List<FailedIssuedCoupon> failures = failedIssuedCouponRepository.findAll();
            assertThat(failures).hasSize(1);

            boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
            assertThat(isIssued).isFalse();
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
            verify(couponIssuanceService, times(1)).reissue(userId, couponId, failedIssuedCoupon.getId());

            boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
            assertThat(isIssued).isTrue();

            FailedIssuedCoupon result = failedIssuedCouponRepository.findById(failedIssuedCoupon.getId()).orElseThrow();
            assertThat(result.isResolved()).isTrue();
        });
    }

    @Test
    @DisplayName("[재처리 - 실패] 재처리 중 네트워크 혹은 DB 오류가 발생하면, 원본 실패 이력의 재시도 횟수가 증가한다.")
    void fail_increase_retry_count_when_reissue_fails() {
        // given
        final Coupon coupon = createCoupon(1);
        final UUID userId = UUID.randomUUID();
        final UUID couponId = coupon.getId();
        FailedIssuedCoupon failedIssuedCoupon = failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));

        CouponIssueMessage message = new CouponIssueMessage(userId, couponId, failedIssuedCoupon.getId());

        doThrow(new RuntimeException("Network Error or DB Connection Failed during reissue"))
                .when(couponIssuanceService).reissue(userId, couponId, failedIssuedCoupon.getId());

        // when
        kafkaTemplate.send(retryTopic, message);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(failedCouponRetryCountIncrease, times(1)).retryCountIncrease(failedIssuedCoupon.getId()));
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
