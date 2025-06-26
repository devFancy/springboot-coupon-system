package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.exception.IssuedCouponNotFoundException;
import dev.be.coupon.domain.coupon.CouponRepository;
import dev.be.coupon.domain.coupon.IssuedCouponRepository;
import dev.be.coupon.domain.coupon.exception.CouponAlreadyUsedException;
import dev.be.coupon.domain.coupon.exception.InvalidCouponTypeException;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import dev.be.coupon.infra.kafka.producer.CouponIssueProducer;
import dev.be.coupon.infra.redis.AppliedUserRepository;
import dev.be.coupon.infra.redis.CouponCacheRepository;
import dev.be.coupon.infra.redis.CouponCountRedisRepository;
import dev.be.coupon.infra.redis.CouponWaitingQueueRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CouponServiceTest 주의사항:
 * <p>
 * 이 테스트는 Kafka Consumer 가 실제 DB(JPA Repository)에 접근해 데이터를 저장하는 구조이므로,
 * 테스트에서도 Spring Context 에서 관리하는 실제 JPA 기반 Repository 를 사용해야 합니다.
 * <p>
 * 특히, InMemoryCouponRepository 또는 InMemoryIssuedCouponRepository 를 직접 생성해 사용할 경우,
 * Kafka Consumer 가 접근하는 Repository 와 분리되어 테스트 결과가 어긋나게 됩니다.
 * <p>
 * 따라서 아래 Repository 들은 모두 @Autowired 로 주입 받아야 합니다:
 * - CouponRepository
 * - IssuedCouponRepository
 * 해당 테스트를 하기 위한 사전 준비 사항
 * - Docker Compose 실행 - MySQL, Redis, Kafka 구동되어야 함
 * - Kafka Application 실행
 */
@SpringBootTest
class CouponServiceTest {

    private CouponService couponService;
    private FakeUserRoleChecker userRoleChecker;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private CouponWaitingQueueRepository couponWaitingQueueRepository;

    @BeforeEach
    void setUp() {
        userRoleChecker = new FakeUserRoleChecker();
        userRoleChecker.updateIsAdmin(true); // 기본값으로 관리자 권한을 부여함


        couponService = new CouponService(
                couponRepository,
                issuedCouponRepository,
                userRoleChecker,
                couponWaitingQueueRepository
        );
    }

    @DisplayName("관리자가 쿠폰을 생성한다.")
    @Test
    void success_coupon() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when
        final CouponCreateResult actual = couponService.create(expected);

        // then
        assertThat(actual.id()).isNotNull();
        assertThat(actual.name()).isEqualTo(expected.name());
    }

    @DisplayName("쿠폰을 생성할 때 기존 유형에 없으면 예외가 발생한다.")
    @ParameterizedTest(name = "쿠폰 유형: {0}")
    @ValueSource(strings = {"KHICKEN", "BIZZA", "VURGER"})
    void should_throw_exception_when_coupon_type_is_invalid(final String type) {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", type, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when & then
        assertThatThrownBy(() -> couponService.create(expected)).isInstanceOf(InvalidCouponTypeException.class).hasMessage("쿠폰 타입이 지정되지 않았습니다.");
    }

    @DisplayName("쿠폰을 생성할 때 관리자 권한이 아니라면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_user_is_not_admin() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand(adminId, "치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        userRoleChecker.updateIsAdmin(false); // 관리자 권한이 아닌 경우: false

        // when & then
        assertThatThrownBy(() -> couponService.create(expected)).isInstanceOf(UnauthorizedAccessException.class).hasMessage("권한이 없습니다.");
    }

    @DisplayName("발급받지 않은 쿠폰을 사용하려고 하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_not_issued() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID otherUserId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        // when
        final UUID couponId = createAndIssueCoupon(
                adminId, userId, 10, now.minusDays(1), now.plusDays(1)
        );

        // then
        assertThatThrownBy(() -> couponService.usage(new CouponUsageCommand(otherUserId, couponId)))
                .isInstanceOf(IssuedCouponNotFoundException.class)
                .hasMessage("발급되지 않았거나 소유하지 않은 쿠폰입니다.");

    }

    @DisplayName("이미 사용한 쿠폰을 다시 사용하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_already_used() {
        // given
        final UUID adminId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final UUID couponId = createAndIssueCoupon(
                adminId, userId, 1, now.minusDays(1), now.plusDays(1)
        );

        // when
        couponService.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThatThrownBy(() -> couponService.usage(new CouponUsageCommand(userId, couponId)))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");

    }

    private UUID createAndIssueCoupon(final UUID adminId, final UUID userId,
                                      final int quantity,
                                      final LocalDateTime validFrom, final LocalDateTime validUntil) {
        // 1. 쿠폰 생성
        final UUID couponId = couponService.create(new CouponCreateCommand(
                adminId, "치킨 쿠폰", "CHICKEN", quantity, validFrom, validUntil
        )).id();

        // 2. 쿠폰 발급
        final CouponIssueResult issued = couponService.issue(new CouponIssueCommand(userId, couponId));
        assertThat(issued.used()).isFalse();

        // 3. Kafka Consumer 가 비동기로 쿠폰 발급을 저장하는 동안 대기
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(issuedCouponRepository.findByUserIdAndCouponId(userId, couponId))
                        .isPresent());
        return couponId;
    }
}
