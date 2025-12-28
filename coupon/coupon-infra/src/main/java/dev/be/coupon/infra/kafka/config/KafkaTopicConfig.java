package dev.be.coupon.infra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * NOTE:
 * - partitions: 파티션 수 >= 전체 컨슈머 수
 * - replica: 운영 환경에서는 장애 허용과 고가용성을 위해 3 이상으로 설정합니다.
 * - config - MIN_IN_SYNC_REPLICAS_CONFIG: 운영 환경에서는 최소 2개 이상의 리플리카에 데이터가 복제되었을 때만 성공으로 간주하여 데이터 유실을 방지합니다.
 */
@Configuration
public class KafkaTopicConfig {

    private final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);

    @Bean
    public NewTopic couponIssueTopic(
            @Value("${kafka.topic.coupon-issue.name}") final String topic,
            @Value("${kafka.topic.coupon-issue.partitions}") final int partitions,
            @Value("${kafka.topic.coupon-issue.replicas}") final int replicas,
            @Value("${kafka.topic.coupon-issue.min-insync-replicas}") final String minInsyncReplicas
    ) {
        log.info("[KafkaTopicConfig] Creating Topic: {}, Partitions: {}, Replicas: {}, Min-ISR: {}",
                topic, partitions, replicas, minInsyncReplicas);

        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsyncReplicas)
                .build();
    }

    @Bean
    public NewTopic couponIssueDlqTopic(
            @Value("${kafka.topic.coupon-issue.name}") final String topic,
            @Value("${kafka.topic.coupon-issue.partitions}") final int partitions,
            @Value("${kafka.topic.coupon-issue.replicas}") final int replicas,
            @Value("${kafka.topic.coupon-issue.min-insync-replicas}") final String minInsyncReplicas
    ) {
        final String dlqTopicName = topic + "-dlq";

        log.info("[KafkaTopicConfig] Creating DLQ Topic: {}, Partitions: {}, Replicas: {}, Min-ISR: {}",
                dlqTopicName, partitions, replicas, minInsyncReplicas);

        return TopicBuilder.name(dlqTopicName)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsyncReplicas)
                .build();
    }
}
