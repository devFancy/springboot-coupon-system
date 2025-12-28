package dev.be.coupon.kafka.consumer.config;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
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

    private final String bootstrapServers;
    private final int concurrency;
    private final long initialInterval;
    private final double multiplier;
    private final int maxAttempts;
    private final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    public KafkaConsumerConfig(
            @Value("${spring.kafka.consumer.bootstrap-servers}") final String bootstrapServers,
            @Value("${spring.kafka.listener.concurrency}") final int concurrency,
            @Value("${spring.kafka.consumer.backoff.initial-interval}") final long initialInterval,
            @Value("${spring.kafka.consumer.backoff.multiplier}") final double multiplier,
            @Value("${spring.kafka.consumer.backoff.max-attempts}") final int maxAttempts
    ) {
        this.bootstrapServers = bootstrapServers;
        this.concurrency = concurrency;
        this.initialInterval = initialInterval;
        this.multiplier = multiplier;
        this.maxAttempts = maxAttempts;
        log.info("""
                [KafkaConsumerConfig] Loaded Settings:
                - Bootstrap Servers: {}
                - Concurrency: {}
                - Retry Initial Interval: {} ms
                - Retry Multiplier: {}
                - Max Attempts: {}
                """, bootstrapServers, concurrency, initialInterval, multiplier, maxAttempts);
    }

    @Bean
    public ConsumerFactory<String, CouponIssueMessage> consumerFactory(final MeterRegistry meterRegistry) {
        JsonDeserializer<CouponIssueMessage> deserializer = new JsonDeserializer<>(CouponIssueMessage.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        // 필수 설정
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "default-group");

        // 세부 설정
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000); // 10분
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");

        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 200);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); // NOTE: Rate Limiter 값에 따라 변동있음

        log.info("""
                [KafkaConsumerConfig] Internal Detailed Settings:
                  - Group ID            : {}
                  - Concurrency         : {}
                  - Max Poll Records    : {}
                  - Max Poll Interval   : {} ms
                  - Session Timeout     : {} ms
                  - Heartbeat Interval  : {} ms
                  - Fetch Min Bytes     : {} bytes
                  - Fetch Max Wait      : {} ms
                  - Assignor Strategy   : {}
                """,
                config.get(ConsumerConfig.GROUP_ID_CONFIG),
                concurrency,
                config.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
                config.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG),
                config.get(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG),
                config.get(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG),
                config.get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
                config.get(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG),
                config.get(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG)
        );

        DefaultKafkaConsumerFactory<String, CouponIssueMessage> factory =
                new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);

        factory.addListener(new MicrometerConsumerListener<>(meterRegistry));

        return factory;
    }

    /**
     * Note: Kafka 리스너 예외 발생 시 'Exponential Backoff' 재시도 처리 및 DLQ 전송
     * - 재시도를 모두 실패하게 되면, DLQ로 전송하여 메시지 유실을 방지한다.
     */
    @Bean
    public DefaultErrorHandler couponIssueErrorHandler(final KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("[COUPON_ISSUE_DLQ] 쿠폰 발급에 대한 재시도 횟수를 초과하여 메시지를 DLQ로 전송합니다. " +
                                    "topic: {}, partition: {},offset: {}, error: {}",
                            record.topic(), record.partition(), record.offset(), exception.getMessage());

                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + "-dlq",
                            record.partition()
                    );
                });

        // NOTE: 재시도 간격: initialInterval x multiplier
        // e.g. initialInterval = 2, multiplier = 2 => 2s -> 4s -> 8s -> 16s
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
        backOff.setMaxAttempts(maxAttempts);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> kafkaListenerContainerFactory(
            final ConsumerFactory<String, CouponIssueMessage> consumerFactory,
            final RecordInterceptor<String, CouponIssueMessage> mdcRecordInterceptor,
            final DefaultErrorHandler couponIssueErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setConcurrency(concurrency);

        factory.setRecordInterceptor(mdcRecordInterceptor);

        factory.setCommonErrorHandler(couponIssueErrorHandler);
        return factory;
    }
}
