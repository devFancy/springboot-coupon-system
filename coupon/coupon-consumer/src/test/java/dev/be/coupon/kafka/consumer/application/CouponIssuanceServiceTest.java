package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.FailedIssuedCoupon;
import dev.be.coupon.infra.jpa.FailedIssuedCouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
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

@ActiveProfiles("test")
@SpringBootTest
class CouponIssuanceServiceTest {

    @Autowired
    private CouponIssuanceService couponIssuanceService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private FailedIssuedCouponJpaRepository failedIssuedCouponRepository;

    // 'REQUIRES_NEW' 전파 속성 테스트를 위한 헬퍼 서비스
    @Autowired
    private TestCallerService testCallerService;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        failedIssuedCouponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("[신규] 신규 쿠폰 발급 요청 시 DB에 정상적으로 저장된다.")
    void success_save_issued_coupon() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        // when
        couponIssuanceService.issue(userId, couponId);

        // then
        boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
        assertThat(isIssued).isTrue();
    }

    @Test
    @DisplayName("[재처리] 재처리 요청시 쿠폰을 저장하고, 실패 이력을 '해결됨' 상태로 변경한다.")
    void success_save_issued_coupon_and_mark_failure_as_resolved() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();
        FailedIssuedCoupon expected = failedIssuedCouponRepository.save(new FailedIssuedCoupon(userId, couponId));

        // when
        couponIssuanceService.reissue(userId, couponId, expected.getId());

        // then
        boolean isIssued = issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId);
        FailedIssuedCoupon actual = failedIssuedCouponRepository.findById(expected.getId()).get();

        Assertions.assertThat(isIssued).isTrue();
        Assertions.assertThat(actual.isResolved()).isTrue();

    }

    @Test
    @DisplayName("[최초 실패] 쿠폰 발급 처리 과정 중 에러가 발생하면, 실패 이력이 DB에 저장된다.")
    void success_save_failed_coupon_when_error_occurs() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        // when
        Assertions.assertThatThrownBy(() -> testCallerService.callRecordAndThrowException(userId, couponId))
                .isInstanceOf(RuntimeException.class);

        // then
        List<FailedIssuedCoupon> results = failedIssuedCouponRepository.findAll();
        assertThat(results).hasSize(1);
    }


    // Test 전용 Configuration
    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestCallerService testCallerService(CouponIssuanceService service) {
            return new TestCallerService(service);
        }
    }

    public static class TestCallerService {
        private final CouponIssuanceService couponIssuanceService;

        public TestCallerService(CouponIssuanceService couponIssuanceService) {
            this.couponIssuanceService = couponIssuanceService;
        }

        @Transactional
        public void callRecordAndThrowException(UUID userId, UUID couponId) {
            couponIssuanceService.recordFailure(userId, couponId);
            throw new RuntimeException("의도적인 롤백 유도");
        }
    }
}
