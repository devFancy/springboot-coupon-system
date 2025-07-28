package dev.be.coupon.kafka.consumer.config;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, CouponIssueMessage> consumerFactory() {
        JsonDeserializer<CouponIssueMessage> deserializer = new JsonDeserializer<>(CouponIssueMessage.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);

        config.put(ConsumerConfig.GROUP_ID_CONFIG, "group_1");
        // 오프셋 자동 커밋을 비활성화 -> 메시지 처리 완료 후 애플리케이션이 직접 커밋 시점을 제어하여, 메시지 유실을 방지
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 한 번의 poll() 호출로 가져올 최대 레코드 수를 지정
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 리스너에서 Acknowledgment.acknowledge()를 명시적으로 호출해야만 오프셋이 커밋합니다.
        factory.getContainerProperties().setObservationEnabled(true); // Micrometer 연동 활성화
        factory.setConcurrency(3); // 토픽의 파티션 수와 일치시켜 병렬 처리 성능을 최적화합니다.
        return factory;
    }
}
