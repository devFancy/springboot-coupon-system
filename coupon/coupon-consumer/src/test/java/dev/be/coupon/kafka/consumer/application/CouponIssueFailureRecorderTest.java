package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.domain.coupon.FailedIssuedCouponRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueFailureRecorderTest {

    @Autowired
    private TestCallerService testCallerService;

    @Autowired
    private FailedIssuedCouponRepository failedIssuedCouponRepository;

    @Test
    @DisplayName("메인 트랜잭션이 롤백되어도 실패 이력은 정상적으로 저장되어야 한다.")
    void recordFailureLog_whenOuterTransactionRollsBack() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        // when
        // '실패하는 호출자(TestCallerService)'를 실행한다. 이 호출자는 내부적으로 롤백을 유발한다.
        assertThatThrownBy(() -> testCallerService.callRecordAndThrowException(userId, couponId))
                .isInstanceOf(RuntimeException.class);

        // then
        // 호출자가 롤백되었음에도, 실패 이력은 DB에 남아있는지 검증한다.
        List<FailedIssuedCoupon> results = failedIssuedCouponRepository.findAllByIsResolvedFalse();
        assertThat(results).hasSize(1);
    }

    /**
     * 테스트에서만 사용할 '실패하는 호출자' 역할을 하는 가상의 서비스
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestCallerService testCallerService(CouponIssueFailureRecorder recorder) {
            return new TestCallerService(recorder);
        }
    }

    public static class TestCallerService {
        private final CouponIssueFailureRecorder recorder;

        public TestCallerService(final CouponIssueFailureRecorder recorder) {
            this.recorder = recorder;
        }

        @Transactional
        public void callRecordAndThrowException(final UUID userId, final UUID couponId) {
            recorder.record(userId, couponId);
            throw new RuntimeException("의도적인 롤백 유도");
        }
    }
}
