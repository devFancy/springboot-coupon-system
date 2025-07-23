package dev.be.coupon.infra.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // 재시도 횟수
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // 프로듀서가 전송을 시도하는 총 시간 (재시도 포함)
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        // 재시도 사이의 대기 시간
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        return new DefaultKafkaProducerFactory<>(config);
    }

    // 카프카 토픽에 데이터를 전송하기 위해 사용할 Kafka Template을 생성
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
