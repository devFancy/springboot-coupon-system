package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueServiceTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.keys("*").forEach(redisTemplate::delete);
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        Objects.requireNonNull(redisTemplate.keys("coupon_count:*")).forEach(redisTemplate::delete);
        Objects.requireNonNull(redisTemplate.keys("applied_user:*")).forEach(redisTemplate::delete);
    }

    @DisplayName("[Consumer] 쿠폰 발급 메시지를 받으면 중복 및 수량 검사 후 DB에 저장한다.")
    @Test
    void should_save_issued_coupon_after_validation() {
        // given
        Coupon coupon = new Coupon(
                "치킨 쿠폰",
                CouponType.CHICKEN,
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10)
        );
        couponRepository.save(coupon);

        UUID userId = UUID.randomUUID();
        UUID couponId = coupon.getId();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        couponIssueService.issue(message);

        // then
        Optional<IssuedCoupon> issuedCoupon = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId);
        assertThat(issuedCoupon).isPresent();

        String count = redisTemplate.opsForValue().get("coupon_count:" + couponId);
        assertThat(count).isEqualTo("1");

        Boolean isMember = redisTemplate.opsForSet().isMember("applied_user:" + couponId, userId.toString());
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("선착순 쿠폰: 100개 한정 쿠폰에 1000명이 동시에 요청 시, 정확히 100개만 발급된다.")
    void issue_for_100_coupons_with_1000_users_concurrently() throws InterruptedException {
        // given
        int totalQuantity = 100;
        int numberOfUsers = 1000;
        Coupon coupon = createCoupon(totalQuantity);
        UUID couponId = coupon.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);

        // when
        IntStream.range(0, numberOfUsers).forEach(i -> {
            UUID userId = UUID.randomUUID();
            executorService.submit(() -> {
                try {
                    couponIssueService.issue(new CouponIssueMessage(userId, couponId));
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long issuedDbCount = issuedCouponRepository.count();
            assertThat(issuedDbCount).isEqualTo(totalQuantity);

            String redisCount = redisTemplate.opsForValue().get("coupon_count:" + couponId);
            assertThat(redisCount).isEqualTo(String.valueOf(totalQuantity));
        });
    }

    @Test
    @DisplayName("중복 발급 요청: 한 명의 유저가 동일 쿠폰에 대해 여러 번 동시에 요청해도, 1회만 발급된다.")
    void issue_for_same_user_multiple_times_concurrently() throws InterruptedException {
        // given
        int totalQuantity = 100;
        int numberOfRequests = 10;
        Coupon coupon = createCoupon(totalQuantity);
        UUID couponId = coupon.getId();
        UUID userId = UUID.randomUUID();

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);

        // when
        IntStream.range(0, numberOfRequests).forEach(i -> {
            executorService.submit(() -> {
                try {
                    couponIssueService.issue(new CouponIssueMessage(userId, couponId));
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            int issuedCount = issuedCouponRepository.countByCouponId(couponId);
            assertThat(issuedCount).isEqualTo(1);
        });
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
