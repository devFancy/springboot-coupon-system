package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.exception.InvalidIssuedCouponException;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.FakeUserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import dev.be.coupon.api.coupon.infrastructure.CouponCountRedisRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class CouponServiceTest {

    private CouponService couponService;
    private FakeUserRoleChecker userRoleChecker;
    private InMemoryIssuedCouponRepository issuedCouponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        CouponRepository couponRepository = new InMemoryCouponRepository();
        issuedCouponRepository = new InMemoryIssuedCouponRepository();
        userRoleChecker = new FakeUserRoleChecker();
        userRoleChecker.updateIsAdmin(true);
        CouponCountRedisRepository couponCountRedisRepository = new CouponCountRedisRepository(redisTemplate);
        couponService = new CouponService(couponRepository, userRoleChecker, issuedCouponRepository, couponCountRedisRepository);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.keys("coupon_count:*").forEach(redisTemplate::delete);
        redisTemplate.keys("lock:coupon:*").forEach(redisTemplate::delete);
    }

    @DisplayName("쿠폰을 생성한다.")
    @Test
    void success_coupon() {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand("치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when
        final CouponCreateResult actual = couponService.create(userID, expected);

        // then
        assertThat(actual.id()).isNotNull();
        assertThat(actual.name()).isEqualTo(expected.name());
    }

    @DisplayName("쿠폰을 생성할 때 기존 유형에 없으면 예외가 발생한다.")
    @ParameterizedTest(name = "쿠폰 유형: {0}")
    @ValueSource(strings = {"KHICKEN", "BIZZA", "VURGER"})
    void should_throw_exception_when_coupon_type_is_invalid(final String type) {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand("치킨", type, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        // when & then
        assertThatThrownBy(() -> couponService.create(userID, expected)).isInstanceOf(InvalidCouponTypeException.class).hasMessage("쿠폰 타입이 지정되지 않았습니다.");
    }

    @DisplayName("쿠폰을 생성할 때 관리자 권한이 아니라면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_user_is_not_admin() {
        // given
        final UUID userID = UUID.randomUUID();
        final CouponCreateCommand expected = new CouponCreateCommand("치킨", "CHICKEN", 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        userRoleChecker.updateIsAdmin(false); // 관리자 권한이 아닌 경우: false

        // when & then
        assertThatThrownBy(() -> couponService.create(userID, expected)).isInstanceOf(UnauthorizedAccessException.class).hasMessage("권한이 없습니다.");
    }

    @DisplayName("사용자가 쿠폰 발급 요청을 하면 쿠폰 발급이 처리된다.")
    @Test
    void success_issued_coupon() {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand createCommand = new CouponCreateCommand("피자 할인 쿠폰", "PIZZA", 10, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult created = couponService.create(UUID.randomUUID(), createCommand);
        final UUID couponId = created.id();

        final CouponIssueCommand issueCommand = new CouponIssueCommand(userId, couponId);

        // when
        final CouponIssueResult result = couponService.issue(issueCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isFalse();
        assertThat(result.issuedAt()).isNotNull();
    }

    @DisplayName("동일한 사용자에게 쿠폰을 1000번 발급 요청해도 중복 발급은 1회만 된다. - 단일 스레드")
    @Test
    void should_only_issue_once_for_same_user() {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand("햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(userId, command);
        final UUID couponId = result.id();

        // when
        for (int i = 0; i < 1000; i++) {
            try {
                couponService.issue(new CouponIssueCommand(userId, couponId));
            } catch (InvalidIssuedCouponException ignore) {
                // 중복 쿠폰 발급 무시
            }
        }

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
    }

    @DisplayName("동일한 사용자에게 쿠폰을 1000번 동시 발급 요청해도 중복 발급은 1회만 된다. - 멀티 스레드")
    @Test
    void should_only_issue_once_for_same_user_multi_thread() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand("햄버거 쿠폰", "BURGER", 1000, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        final CouponCreateResult result = couponService.create(userId, command);
        final UUID couponId = result.id();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(1000);

        // when
        for (int i = 0; i < 1000; i++) {
            executor.execute(() -> {
                try {
                    couponService.issue(new CouponIssueCommand(userId, couponId));
                } catch (InvalidIssuedCouponException ignore) {
                    // 중복 쿠폰 발급 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(1);
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isLessThanOrEqualTo(1);
    }

    @DisplayName("1000명의 사용자에게 수량 500개짜리 쿠폰을 발급하면 최대 500개만 발급된다. - 단일 스레드")
    @Test
    void should_only_issue_up_to_total_quantity_limit() {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand("피자 쿠폰", "PIZZA", 500, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final CouponCreateResult result = couponService.create(adminId, command);
        final UUID couponId = result.id();

        // when
        for (int i = 0; i < 1000; i++) {
            UUID userId = UUID.randomUUID();
            try {
                couponService.issue(new CouponIssueCommand(userId, couponId));
            } catch (InvalidIssuedCouponException ignore) {
                // 수량 초과 예외 무시
            }
        }

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(500);
    }

    @DisplayName("1000명의 사용자에게 동시 발급 요청 시 수량 500개 초과 발급되지 않는다. - 멀티 스레드")
    @Test
    void should_only_issue_up_to_total_quantity_limit_multithreaded() throws InterruptedException {
        // given
        final UUID adminId = UUID.randomUUID();
        final CouponCreateCommand command = new CouponCreateCommand("피자 쿠폰", "PIZZA", 500, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        final UUID couponId = couponService.create(adminId, command).id();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(1000);

        // when
        for (int i = 0; i < 1000; i++) {
            final UUID userId = UUID.randomUUID(); // 각각 다른 사용자
            executor.execute(() -> {
                try {
                    couponService.issue(new CouponIssueCommand(userId, couponId));
                } catch (InvalidIssuedCouponException ignore) {
                    // 수량 초과 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(issuedCouponRepository.countByCouponId(couponId)).isEqualTo(500);
    }

}
