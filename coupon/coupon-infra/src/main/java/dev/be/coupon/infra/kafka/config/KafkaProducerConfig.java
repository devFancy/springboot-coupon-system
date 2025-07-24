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

        // 필수 옵션
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 데이터 전송 신뢰도 설정: 모든 ISR에 복제될 때까지 대기
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // 재시도 횟수: 일시적인 네트워크 문제나 브로커 장애 시 메시지를 재전송할 최대 횟수입니다.
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // 재시도 사이의 대기 시간
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // 프로듀서가 전송을 시도하는 총 시간 (재시도 포함)
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        // 멱등성 활성화
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 배치 처리 설정: 메시지 전송 효율을 높이기 위해 여러 메시지를 모아 배치로 전송합니다.
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        // 압축 타입 설정: 네트워크 대역폭과 저장 공간을 절약하기 위해 메시지 압축을 사용합니다.
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(config);
    }

    // 카프카 토픽에 데이터를 전송하기 위해 사용할 Kafka Template을 생성
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
