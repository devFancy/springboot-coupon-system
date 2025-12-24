package dev.be.coupon.infra.kafka.producer;

import dev.be.coupon.infra.exception.CouponInfraException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponIssueProducerTest {

    private CouponIssueProducer couponIssueProducer;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private final String testTopic = "coupon-issue";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = (KafkaTemplate<String, Object>) mock(KafkaTemplate.class);
        couponIssueProducer = new CouponIssueProducer(kafkaTemplate, testTopic);

    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Kafka 메시지를 성공적으로 발행해야 한다.")
    void should_issue_message_successfully() {
        // given
        final UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn(testTopic);

        SendResult<String, Object> sendResult = (SendResult<String, Object>) mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // when & then
        couponIssueProducer.issue(userId, couponId);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Kafka 발행 실패 시 예외가 발생한다.")
    void should_throw_infra_exception_when_kafka_send_fails() {
        // given
        final  UUID userId = UUID.randomUUID();
        final UUID couponId = UUID.randomUUID();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka Broker Down"));

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // when & then
        assertThatThrownBy(() -> couponIssueProducer.issue(userId, couponId))
                .isInstanceOf(CouponInfraException.class)
                .hasMessage("Kafka 메시지 발행이 실패했습니다.");
    }
}