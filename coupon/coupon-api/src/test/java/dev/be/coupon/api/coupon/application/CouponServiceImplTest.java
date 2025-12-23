package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.dto.OwnedCouponFindResult;
import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.IssuedCoupon;
import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import dev.be.coupon.infra.jpa.CouponJpaRepository;
import dev.be.coupon.infra.jpa.IssuedCouponJpaRepository;
import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.be.coupon.domain.coupon.CouponFixtures.정상_쿠폰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * NOTE: 해당 테스트를 하기 위한 사전 준비 사항
 * - Docker Compose 실행 (MySQL, Redis 가 구동되어야 한다.)
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=${random.uuid}",
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"coupon-issue-test"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0",
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CouponServiceImplTest {

    @Autowired
    private CouponServiceImpl couponServiceImpl;

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponRepository;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${kafka.topic.coupon-issue}")
    private String issueTopic;

    private Consumer<String, CouponIssueMessage> testConsumer;

    @BeforeEach
    void setUp() {
        // NOTE: 테스트의 목적이 메시지 전송 여부 확인에 있기 때문에, 여기서는 자동 커밋으로 설정했다.
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("coupon-issue-test", "true", embeddedKafkaBroker);
        DefaultKafkaConsumerFactory<String, CouponIssueMessage> factory = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new JsonDeserializer<>(CouponIssueMessage.class));

        testConsumer = factory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, issueTopic);
    }

    @AfterEach
    void tearDown() {
        testConsumer.close();
        issuedCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
    }

    @DisplayName("사용자가 쿠폰 발급을 요청하면 성공적으로 접수되고 Kafka 메시지가 발행된다.")
    @Test
    void should_issue_successfully_when_request_is_valid() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(100).getId();
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        CouponIssueRequestResult result = couponServiceImpl.issue(command);

        // then
        assertThat(result).isEqualTo(CouponIssueRequestResult.SUCCESS);

        ConsumerRecord<String, CouponIssueMessage> received = KafkaTestUtils.getSingleRecord(testConsumer, issueTopic);
        assertThat(received.value().userId()).isEqualTo(userId);
        assertThat(received.value().couponId()).isEqualTo(couponId);
    }

    @DisplayName("동일한 사용자가 중복으로 쿠폰 발급을 요청하면 'DUPLICATE' 를 반환하고 메시지는 발행되지 않는다.")
    @Test
    void should_return_duplicate_when_already_issued() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(10).getId();
        final CouponIssueCommand command = new CouponIssueCommand(userId, couponId);

        // when
        couponServiceImpl.issue(command);
        CouponIssueRequestResult secondResult = couponServiceImpl.issue(command);

        // then
        assertThat(secondResult).isEqualTo(CouponIssueRequestResult.DUPLICATE);
        ConsumerRecords<String, CouponIssueMessage> records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(2));
        assertThat(records.count()).isEqualTo(1);
    }

    @DisplayName("쿠폰이 모두 소진된 후 추가 발급을 요청하면 'SOLD_OUT'을 반환한다.")
    @Test
    void should_return_sold_out_when_quantity_is_exhausted() {
        // given
        final UUID couponId = createCoupon(1).getId();
        final UUID user1 = UUID.randomUUID();
        final UUID user2 = UUID.randomUUID();

        // when
        CouponIssueRequestResult result1 = couponServiceImpl.issue(new CouponIssueCommand(user1, couponId));
        CouponIssueRequestResult result2 = couponServiceImpl.issue(new CouponIssueCommand(user2, couponId));

        // then
        assertThat(result1).isEqualTo(CouponIssueRequestResult.SUCCESS);
        assertThat(result2).isEqualTo(CouponIssueRequestResult.SOLD_OUT);
    }

    @DisplayName("여러명의 사용자가 동시에 요청해도 쿠폰 총 수량만 발급되도록 한다.")
    @Test
    void should_issue_exactly_as_much_as_quantity_under_high_concurrency() throws InterruptedException {
        // given
        final int totalQuantity = 50;
        final UUID couponId = createCoupon(totalQuantity).getId();

        final int threadCount = 100;
        final int requestCount = 10000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        // when
        for (int i = 0; i < requestCount; i++) {
            final UUID userId = UUID.randomUUID();
            executorService.submit(() -> {
                try {
                    couponServiceImpl.issue(new CouponIssueCommand(userId, couponId));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // then
        ConsumerRecords<String, CouponIssueMessage> records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(totalQuantity);
    }

    @DisplayName("사용자가 발급받은 쿠폰이 1개 이상 있을 떄, 쿠폰 목록을 리스트로 반환한다.")
    @Test
    void should_return_all_owned_coupons_when_user_has_coupons() {
        // given
        final UUID userId = UUID.randomUUID();

        // 1. 사용한 쿠폰
        final Coupon coupon1 = createCoupon(10);
        final IssuedCoupon issuedCoupon1 = new IssuedCoupon(userId, coupon1.getId());
        issuedCoupon1.use(LocalDateTime.now().minusDays(1));
        issuedCouponRepository.save(issuedCoupon1);

        // 2. 미사용한 쿠폰
        final Coupon coupon2 = createCoupon(10);
        issueCoupon(userId, coupon2.getId());

        // when
        List<OwnedCouponFindResult> results = couponServiceImpl.getOwnedCoupons(userId);

        // then
        assertThat(results).hasSize(2);
        assertThat(results).extracting("couponId", "used")
                .containsExactlyInAnyOrder(
                        tuple(coupon1.getId(), true),
                        tuple(coupon2.getId(), false)
                );
    }

    @DisplayName("사용자가 발급받은 쿠폰이 없으면, 빈 리스트를 반환한다.")
    @Test
    void should_return_empty_list_when_no_coupons_exist() {
        // given
        final UUID userId = UUID.randomUUID();

        // when
        List<OwnedCouponFindResult> results = couponServiceImpl.getOwnedCoupons(userId);

        // then
        assertThat(results).isEmpty();
    }

    @DisplayName("발급된 쿠폰의 원본 쿠폰이 삭제된 경우 해당 쿠폰을 제외하고 반환한다.")
    @Test
    void should_filter_out_coupons_when_original_definition_is_deleted() {
        // given
        final UUID userId = UUID.randomUUID();

        final Coupon coupon = createCoupon(10);
        issueCoupon(userId, coupon.getId());

        final Coupon deletedCoupon = createCoupon(10);
        issueCoupon(userId, deletedCoupon.getId());
        couponRepository.delete(deletedCoupon);

        // when
        List<OwnedCouponFindResult> results = couponServiceImpl.getOwnedCoupons(userId);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).couponId()).isEqualTo(coupon.getId());
    }

    @DisplayName("사용자가 발급된 쿠폰을 사용하면 사용 처리된다.")
    @Test
    void should_use_coupon_successfully_when_valid() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(1).getId();
        issueCoupon(userId, couponId);

        // when
        final CouponUsageResult result = couponServiceImpl.usage(new CouponUsageCommand(userId, couponId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.used()).isTrue();
        assertThat(result.usedAt()).isNotNull();
    }

    @DisplayName("사용자가 소유하지 않은 쿠폰을 사용하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_is_not_owned() {
        // given
        final UUID userA = UUID.randomUUID(); // 쿠폰 소유자
        final UUID userB = UUID.randomUUID(); // 사용 시도자
        final UUID couponId = createCoupon(1).getId();
        issueCoupon(userA, couponId); // 쿠폰은 userA에게 발급됨

        // when & then
        // userB가 userA의 쿠폰을 사용하려고 할 때 예외가 발생해야 함
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userB, couponId)))
                .isInstanceOf(CouponException.class)
                .hasMessage("발급되지 않았거나 소유하지 않은 쿠폰입니다.");
    }

    @DisplayName("사용자가 이미 사용한 쿠폰을 재사용하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_is_already_used() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = createCoupon(100).getId();
        issueCoupon(userId, couponId);

        couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)); // 첫 번째 사용

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userId, couponId)))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }

    @DisplayName("유효 기간이 만료된 쿠폰을 사용하면 예외가 발생한다.")
    @Test
    void should_throw_exception_when_coupon_is_expired() throws InterruptedException {
        // given
        final UUID userId = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon = new Coupon(
                "만료된 쿠폰",
                CouponType.BURGER,
                CouponDiscountType.FIXED,
                BigDecimal.valueOf(10_000L),
                100,
                now.plusSeconds(1)
        );
        couponRepository.save(expiredCoupon);
        issueCoupon(userId, expiredCoupon.getId());

        Thread.sleep(1001);

        // when & then
        assertThatThrownBy(() -> couponServiceImpl.usage(new CouponUsageCommand(userId, expiredCoupon.getId())))
                .isInstanceOf(CouponDomainException.class);
    }

    private Coupon createCoupon(final int totalQuantity) {
        Coupon coupon = 정상_쿠폰(totalQuantity);
        return couponRepository.save(coupon);
    }

    private void issueCoupon(final UUID userId, final UUID couponId) {
        IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);
    }
}
