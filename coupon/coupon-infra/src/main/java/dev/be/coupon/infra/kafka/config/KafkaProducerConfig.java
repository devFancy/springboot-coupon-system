package dev.be.coupon.infra.kafka.config;

import dev.be.coupon.infra.kafka.interceptor.MdcProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.kafka.producer.bootstrap-servers}")
    private String bootstrapServers;

    private final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // 필수 설정
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 세부 설정
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, MdcProducerInterceptor.class.getName());

        log.info("""
                [KafkaProducerConfig] Performance Settings:
                  - Bootstrap Servers   : {}
                  - Acks Mode           : {}
                  - Idempotence Enabled : {}
                  - Batch Size          : {} bytes
                  - Linger Time         : {} ms
                  - Compression Type    : {}
                  - Delivery Timeout    : {} ms
                """,
                bootstrapServers,
                config.get(ProducerConfig.ACKS_CONFIG),
                config.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG),
                config.get(ProducerConfig.BATCH_SIZE_CONFIG),
                config.get(ProducerConfig.LINGER_MS_CONFIG),
                config.get(ProducerConfig.COMPRESSION_TYPE_CONFIG),
                config.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)
        );

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
