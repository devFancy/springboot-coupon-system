package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.infra.jpa.FailedIssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import dev.be.coupon.kafka.consumer.support.error.CouponConsumerException;
import dev.be.coupon.kafka.consumer.support.error.ErrorType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest
class CouponIssueFailureHandlerTest {

    @Autowired
    private CouponIssueFailureHandler failureHandler;

    @SpyBean
    private CouponIssuanceService couponIssuanceService;

    @SpyBean
    private FailedCouponRetryCountIncrease failedCouponRetryCountIncrease;

    @MockBean
    private Acknowledgment ack;

    @Autowired
    private FailedIssuedCouponJpaRepository failedIssuedCouponRepository;

    @AfterEach
    void tearDown() {
        failedIssuedCouponRepository.deleteAllInBatch();
        reset(couponIssuanceService, failedCouponRetryCountIncrease, ack);
    }

    @Test
    @DisplayName("[신규 - 실패] 비즈니스 예외 발생 시, 실패 이력 없이 메시지만 ack한다.")
    void ack_message_when_business_exception_on_handle() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, couponId, null);
        final Exception e = new CouponDomainException("쿠폰 상태가 활성 상태가 아닙니다.");

        // when
        failureHandler.handle(message, ack, e);

        // then
        verify(ack, times(1)).acknowledge();
        verify(couponIssuanceService, never()).recordFailure(any(), any());

        assertThat(failedIssuedCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("[신규 - 실패] 인프라 예외 발생 시, 실패 이력을 저장하고 ack한다.")
    void record_failure_and_ack_message_when_temporary_exception_on_handle() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, couponId, null);
        final Exception e = new RuntimeException("인프라 장애");

        // when
        failureHandler.handle(message, ack, e);

        // then
        verify(ack, times(1)).acknowledge();
        assertThat(failedIssuedCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[신규 - 실패] DB 다운 시, 실패 이력 저장을 '시도'하고 예외를 던진다 (ack 안함).")
    void record_failure_and_rethrow_when_fatal_exception_on_handle() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        final CouponIssueMessage message = new CouponIssueMessage(userId, couponId, null);
        final Exception e = new RuntimeException("DB 다운");

        doThrow(new RuntimeException("DB Connection Pool is down"))
                .when(couponIssuanceService).recordFailure(userId, couponId);

        // when & then
        assertThatThrownBy(() -> failureHandler.handle(message, ack, e))
                .isInstanceOf(CouponConsumerException.class)
                .hasMessageContaining(ErrorType.COUPON_ISSUANCE_FAILED.getMessage());

        verify(couponIssuanceService, times(1)).recordFailure(userId, couponId);
        verify(ack, never()).acknowledge();

        assertThat(failedIssuedCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("[재처리 - 실패] 비즈니스 예외 발생 시, 재시도 횟수를 증가시키고 ack한다.")
    void increase_retry_count_and_ack_message_when_business_exception_on_retry() {
        // given
        final FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.save(new FailedIssuedCoupon(UUID.randomUUID(), UUID.randomUUID()));
        final UUID failedId = failedCoupon.getId();

        final CouponIssueMessage message = new CouponIssueMessage(UUID.randomUUID(), UUID.randomUUID(), failedId);
        final Exception e = new CouponDomainException("비즈니스 예외");

        assertThat(failedCoupon.getRetryCount()).isZero();

        // when
        failureHandler.handleRetry(message, ack, e);

        // then
        verify(ack, times(1)).acknowledge();

        FailedIssuedCoupon result = failedIssuedCouponRepository.findById(failedId).orElseThrow();
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[재처리 - 실패] 인프라 예외 발생 시, 재시도 횟수를 증가시키고 ack한다.")
    void increase_retry_count_and_ack_message_when_temporary_exception_on_retry() {
        // given
        final FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.save(new FailedIssuedCoupon(UUID.randomUUID(), UUID.randomUUID()));
        final UUID failedId = failedCoupon.getId();

        final CouponIssueMessage message = new CouponIssueMessage(UUID.randomUUID(), UUID.randomUUID(), failedId);
        final Exception e = new RuntimeException("인프라 장애");

        assertThat(failedCoupon.getRetryCount()).isZero();

        // when
        failureHandler.handleRetry(message, ack, e);

        // then
        verify(ack, times(1)).acknowledge();

        FailedIssuedCoupon result = failedIssuedCouponRepository.findById(failedId).orElseThrow();
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[재처리 - 실패] DB 다운 시, 횟수 증가를 '시도'하고 예외를 던진다 (ack 안함).")
    void increase_retry_count_and_rethrow_when_fatal_exception_on_retry() {
        // given
        final FailedIssuedCoupon failedCoupon = failedIssuedCouponRepository.save(new FailedIssuedCoupon(UUID.randomUUID(), UUID.randomUUID()));
        final UUID failedId = failedCoupon.getId();

        final CouponIssueMessage message = new CouponIssueMessage(UUID.randomUUID(), UUID.randomUUID(), failedId);
        final Exception e = new RuntimeException("DB 다운");

        doThrow(new RuntimeException("DB Connection Pool is down"))
                .when(failedCouponRetryCountIncrease).retryCountIncrease(failedId);

        // when & then
        assertThatThrownBy(() -> failureHandler.handleRetry(message, ack, e))
                .isInstanceOf(CouponConsumerException.class)
                .hasMessageContaining(ErrorType.COUPON_ISSUANCE_RETRY_FAILED.getMessage());

        verify(failedCouponRetryCountIncrease, times(1)).retryCountIncrease(failedId);
        verify(ack, never()).acknowledge();

        FailedIssuedCoupon result = failedIssuedCouponRepository.findById(failedId).orElseThrow();
        assertThat(result.getRetryCount()).isZero();
    }
}
