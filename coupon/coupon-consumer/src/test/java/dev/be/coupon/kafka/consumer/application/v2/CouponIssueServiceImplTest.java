package dev.be.coupon.kafka.consumer.application.v2;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueServiceImplTest {


    @Autowired
    private CouponIssueServiceImpl couponIssueService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;


    @AfterEach
    void tearDown() {
        redisTemplate.keys("*").forEach(redisTemplate::delete);
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @DisplayName("[Consumer V2] 쿠폰 발급 메시지를 받으면 DB에 저장한다.")
    @Test
    void should_save_issued_coupon_after_validation() {
        // given
        Coupon coupon = createCoupon(10);
        UUID userId = UUID.randomUUID();
        UUID couponId = coupon.getId();
        CouponIssueMessage message = new CouponIssueMessage(userId, couponId);

        // when
        couponIssueService.issue(message);

        // then
        Optional<IssuedCoupon> issuedCoupon = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId);
        assertThat(issuedCoupon).isPresent();
    }

    @Test
    @DisplayName("[Consumer V2] 선착순 쿠폰: 100개 한정 쿠폰에 10,000명이 동시에 요청 시, DB의 쿠폰 수량만큼만 발급된다.")
    void issue_for_100_coupons_with_1000_users_concurrently() throws InterruptedException {
        // given
        int totalQuantity = 100;
        int numberOfUsers = 10000;
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
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long issuedDbCount = issuedCouponRepository.count();
            assertThat(issuedDbCount).isLessThanOrEqualTo(totalQuantity);
        });
    }

    @Test
    @DisplayName("[Consumer V2] 중복 발급 요청: 한 명의 유저가 동일 쿠폰에 대해 여러 번 동시에 요청해도, 1회만 발급된다.")
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
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long issuedCountForUser = issuedCouponRepository.findAll().stream()
                    .filter(ic -> ic.getUserId().equals(userId))
                    .count();
            assertThat(issuedCountForUser).isEqualTo(1);
        });
    }

    private Coupon createCoupon(int totalQuantity) {
        Coupon coupon = new Coupon(
                "선착순 쿠폰 V2",
                CouponType.CHICKEN,
                totalQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10)
        );
        return couponRepository.save(coupon);
    }
}
