package dev.be.coupon.kafka.consumer.config;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.consumer.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, CouponIssueMessage> consumerFactory() {
        JsonDeserializer<CouponIssueMessage> deserializer = new JsonDeserializer<>(CouponIssueMessage.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        // 공통 필수 설정
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "group_1");

        // 1. 신뢰성 관련 설정
        // 1-1) 오프셋 수동 커밋 설정 -> 메시지 처리 완료 후 애플리케이션이 직접 커밋 시점을 제어하여, 메시지 유실을 방지.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 1-2) 가장 최신 메시지, 즉 컨슈머가 실행된 이후에 들어오는 메시지부터 읽기 시작한다.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // 2. 안정성 관련 설정
        // 2-1) 컨슈머가 브로커(그룹 코디네이터)에게 하트비트를 보내지 않고 버틸 수 있는 최대 시간(기본값: 10,000 ms = 10초)
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        // 2-2) 컨슈머가 얼마자 자주 하트비트를 보낼지 결정하는 주기. (기본값: 3,000ms = 3초)
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        // 2-3) poll()로 가져온 메시지를 처리하는 데 허용된 최대 시간. (기본값: 300,000ms = 5분)
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000); // 10분
        // 2-4) 리밸런싱 시 중단을 최소화하는 '협력적 리밸런스' 전략 사용.
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");

        // 3. 성능 관련 설정
        // 3-1) poll() 요청 시 브로커가 응답해야 할 최소 데이터 크기. (기본값: 1 byte)
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 2);
        // 3-2) poll() 요청 시 브로커에서 대기할 최대 시간. (기본값: 500ms)
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 1000);
        // 3-3) 컨슈머가 브로커로부터 한 번의 poll() 호출로 가져올 최대 메시지 개수. (기본값: 500)
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * Note: Kafka 리스너 예외 발생 시 'Exponential Backoff' 재시도 및 DLQ 전송 핸들러
     * [동작]
     * - 1. Exponential Backoff (최대 5회) 정책에 따라 메시지 재시도를 수행합니다.
     * - 2. 5회 재시도 모두 실패 시, DeadLetterPublishingRecoverer 를 사용하여
     * - 메시지를 '(DLQ)'으로 전송하여 데이터 유실을 방지합니다.
     * [TODO]
     * - 현재는 운영자가 DLQ에 메시지가 쌓이는지 모니터링하고, 장애 복구 후 수동 조치하는 방식
     * - 이 DLQ를 소비하여 자동으로 처리하는 별도의 스케줄러(배치)를 만들 수 있음.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + "-db-down-dlq", // e.g. "coupon-issue-db-down-dlq"
                        record.partition()
                ));

        // - initialInterval: 2초(2000L)부터 시작
        // - multiplier: 2.0배씩 간격 증가 (2s -> 4s -> 8s -> 16s -> 32s)
        // - maxAttempts: 최대 5번 재시도
        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
        backOff.setMaxAttempts(5);

        // backOff으로 재시도 횟수만큼 실행하고, 그래도 실패하면 recoverer(DLQ) 실행
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, CouponIssueMessage> consumerFactory,
            RecordInterceptor<String, CouponIssueMessage> mdcRecordInterceptor,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 리스너에서 Acknowledgment.acknowledge()를 명시적으로 호출해야만 오프셋이 커밋합니다.
        factory.getContainerProperties().setObservationEnabled(true); // Micrometer 연동 활성화
        factory.setConcurrency(3); // 토픽의 파티션 수와 일치시켜 병렬 처리 성능을 최적화합니다.

        factory.setRecordInterceptor(mdcRecordInterceptor);

        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
