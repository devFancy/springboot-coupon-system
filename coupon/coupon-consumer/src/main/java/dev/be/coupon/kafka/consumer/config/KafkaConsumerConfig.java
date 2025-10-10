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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
        // 1-2) 새로운 컨슈머 그룹이 처음 토픽을 읽을 때, 가장 오래된 메시지부터 읽도록 설정
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

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

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, CouponIssueMessage> consumerFactory,
            RecordInterceptor<String, CouponIssueMessage> mdcRecordInterceptor
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 리스너에서 Acknowledgment.acknowledge()를 명시적으로 호출해야만 오프셋이 커밋합니다.
        factory.getContainerProperties().setObservationEnabled(true); // Micrometer 연동 활성화
        factory.setConcurrency(3); // 토픽의 파티션 수와 일치시켜 병렬 처리 성능을 최적화합니다.

        factory.setRecordInterceptor(mdcRecordInterceptor);
        return factory;
    }
}
