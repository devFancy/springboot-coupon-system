package dev.be.coupon.infra.kafka.config;


import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    @DisplayName("KafkaProducerConfig의 producerFactory가 올바른 설정을 포함하는지 확인한다.")
    void check_producerFactory_configurations() {

        // given
        KafkaProducerConfig config = new KafkaProducerConfig();

        ProducerFactory<String, Object> producerFactory = config.producerFactory();

        // when & then
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);

        Map<String, Object> configs = producerFactory.getConfigurationProperties();

        // 필수 옵션 확인
        assertThat(configs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(configs.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(configs.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JsonSerializer.class);

        // 세부 옵션 확인
        assertThat(configs.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(configs.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(3);
        assertThat(configs.get(ProducerConfig.RETRY_BACKOFF_MS_CONFIG)).isEqualTo(1000);
        assertThat(configs.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(120000);
        assertThat(configs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
        assertThat(configs.get(ProducerConfig.BATCH_SIZE_CONFIG)).isEqualTo(16384);
        assertThat(configs.get(ProducerConfig.LINGER_MS_CONFIG)).isEqualTo(10);
        assertThat(configs.get(ProducerConfig.COMPRESSION_TYPE_CONFIG)).isEqualTo("snappy");
    }

    @Test
    @DisplayName("KafkaProducerConfig의 kafkatemplate 빈이 올바르게 생성되는지 확인한다.")
    void check_KafkaTemplate_creation() {

        // given
        KafkaProducerConfig config = new KafkaProducerConfig();

        // when
        KafkaTemplate<String, Object> kafkaTemplate = config.kafkaTemplate();

        // then
        assertThat(kafkaTemplate).isNotNull();
    }
}
