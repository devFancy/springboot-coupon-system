package dev.be.coupon.kafka.consumer.application;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
class CouponIssuanceConsumerTest {


    @Autowired
    private CouponIssuanceServiceImpl couponIssueService;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @Autowired
    private CouponJpaRepository couponRepository;

    @AfterEach
    void tearDown() {
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("중복 발급 요청: 한 명의 유저가 동일 쿠폰에 대해 여러 번 동시에 요청해도, 1회만 발급된다.")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void issue_for_same_user_multiple_times_concurrently() throws InterruptedException {
        // given
        final int totalQuantity = 100;
        final int numberOfRequests = 10;
        final Coupon coupon = createCoupon(totalQuantity);
        final UUID couponId = coupon.getId();
        final UUID userId = UUID.randomUUID();

        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final CountDownLatch latch = new CountDownLatch(numberOfRequests);

        // when
        IntStream.range(0, numberOfRequests).forEach(i -> executorService.submit(() -> {
            try {
                couponIssueService.process(new CouponIssueMessage(userId, couponId));
            } catch (Exception e) {
            } finally {
                latch.countDown();
            }
        }));

        latch.await();
        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        // then
        // 모든 스레드의 작업이 끝난 후, 최종적으로 DB에 쿠폰이 1개만 발급되었는지 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long issuedCountForUser = issuedCouponRepository.findAll().stream()
                    .filter(ic -> ic.getUserId().equals(userId))
                    .count();
            assertThat(issuedCountForUser).isEqualTo(1);
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
