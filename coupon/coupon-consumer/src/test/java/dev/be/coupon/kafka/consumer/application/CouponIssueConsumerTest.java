package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.infra.jpa.CouponIssueFailedEventJpaRepository;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static dev.be.coupon.domain.coupon.CouponFixtures.정상_쿠폰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * NOTE: 해당 테스트를 하기 위한 사전 준비 사항
 * - Docker Compose 실행 (MySQL, Redis 가 구동되어야 한다.)
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=${random.uuid}",
        "spring.kafka.consumer.backoff.initial-interval=100",
        "spring.kafka.consumer.backoff.multiplier=1.0",
        "spring.kafka.consumer.backoff.max-attempts=5",
})
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "coupon-issue-test",
                "coupon-issue-test-dlq"
        },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0",
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CouponIssueConsumerTest {

    @Value("${kafka.topic.coupon-issue.name}")
    private String issueTopic;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private CouponIssueFailedEventJpaRepository failedEventRepository;

    @SpyBean
    private CouponIssueService couponIssueService;


    @AfterEach
    void tearDown() {
        failedEventRepository.deleteAllInBatch();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        reset(couponIssueService);
    }

    @Test
    @DisplayName("쿠폰 발급 요청이 들어오면 데이터 유실 없이 모두 처리된다.")
    void should_process_all_coupons_without_loss_under_high_load() {
        // given
        final int messageCount = 100;
        final Coupon coupon = createCoupon();

        // when
        for (int i = 0; i < messageCount; i++) {
            kafkaTemplate.send(issueTopic, new CouponIssueMessage(UUID.randomUUID(), coupon.getId()));
        }

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int finalIssuedCount = issuedCouponRepository.countByCouponId(coupon.getId());
            assertThat(finalIssuedCount).isEqualTo(messageCount);
        });
    }

    @Test
    @DisplayName("비즈니스 예외 발생 시 재시도 없이 ACK 처리한다.")
    void should_ack_immediately_when_business_exception_occurs() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        doThrow(new CouponConsumerException(ErrorType.COUPON_ALREADY_ISSUED))
                .when(couponIssueService).issue(any(), any());

        // when
        kafkaTemplate.send(issueTopic, message);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(couponIssueService, times(1)).issue(any(), any()));
    }

    @Test
    @DisplayName("데이터 무결성 위반(DB 중복 Key) 발생 시 재시도 없이 ACK 처리한다.")
    void should_ack_immediately_when_data_integrity_violation_occurs() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // NOTE: Service가 실행될 때, DB 레벨의 중복 에러(DataIntegrityViolationException)가 터진다고 가정
        doThrow(new DataIntegrityViolationException("Duplicate Entry"))
                .when(couponIssueService).issue(any(), any());

        // when
        kafkaTemplate.send(issueTopic, message);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(couponIssueService, times(1)).issue(any(), any()));
    }

    @Test
    @DisplayName("일시적인 장애로 처음에 실패하더라도, 재시도 횟수 내에 성공하면 쿠폰이 정상 발급되고 DLQ로 가지 않는다.")
    void should_issue_successfully_after_retries_on_temporary_failure() {
        // given
        final Coupon coupon = createCoupon();
        final UUID userId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, coupon.getId());

        // NOTE: 메시지 전송 전에 미리 정의함
        doThrow(new RuntimeException("Temporary DB Error 1")).doThrow(new RuntimeException("Temporary DB Error 2")).doCallRealMethod().when(couponIssueService).issue(any(UUID.class), any(UUID.class));

        // when
        kafkaTemplate.send(issueTopic, message);

        // NOTE: 재시도 횟수와 시간 간격에 따라 timeout 조정 필요
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean exists = issuedCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId());
            assertThat(exists).isTrue();
        });
        verify(couponIssueService, times(3)).issue(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("지속적인 시스템 장애로 모든 재시도가 실패하면, 메시지는 최종적으로 DLQ로 전송된다.")
    void should_send_to_dlq_when_all_retries_fail() {
        // given
        final Coupon coupon = createCoupon();
        final UUID userId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, coupon.getId());

        doThrow(new RuntimeException("Main Consumer Error"))
                .doThrow(new RuntimeException("Main Consumer Error"))
                .doThrow(new RuntimeException("Main Consumer Error"))
                .doThrow(new RuntimeException("Main Consumer Error"))
                .doThrow(new RuntimeException("Main Consumer Error"))
                .doCallRealMethod() // 6번째 호출(DLQ)에서는 정상 실행
                .when(couponIssueService).issue(any(UUID.class), any(UUID.class));

        // when
        kafkaTemplate.send(issueTopic, message);

        // then
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean exists = issuedCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId());
            assertThat(exists).isTrue();
        });

        // NOTE: 최초 실행 1회 + 재시도 5회 = 총 6회
        verify(couponIssueService, times(6)).issue(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("DLQ 재처리마저 실패하면 실패 이력이 DB에 저장된다.")
    void should_save_failed_event_when_dlq_retry_fails() {
        // given
        final Coupon coupon = createCoupon();
        final UUID userId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, coupon.getId());

        // 시나리오: 메인 리스너(5회) + DLQ 리스너(1회) 모두 실패
        doThrow(new RuntimeException("Persistent Error"))
                .when(couponIssueService).issue(any(UUID.class), any(UUID.class));

        // when
        kafkaTemplate.send(issueTopic, message);

        // then
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = failedEventRepository.count();
            assertThat(count).isEqualTo(1);
        });

        // 1. 발급 시도 횟수 검증 - 메인 횟수(초기 시도 + 재시도 횟수) + DLQ 1회
        verify(couponIssueService, atLeast(6)).issue(any(UUID.class), any(UUID.class));
        verify(couponIssueService, times(1))
                .saveFailedEvent(eq(userId), eq(coupon.getId()), anyString(), anyString());
    }

    private Coupon createCoupon() {
        Coupon coupon = 정상_쿠폰(100);
        return couponRepository.save(coupon);
    }
}
