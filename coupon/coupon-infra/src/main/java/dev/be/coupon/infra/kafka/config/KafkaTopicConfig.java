package dev.be.coupon.infra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic couponIssueTopic(@Value("${kafka.topic.coupon-issue}") final String topic) {
        return TopicBuilder.name(topic)
                .partitions(3) // 파티션 수 >= 전체 컨슈머 수 (컨슈머가 파티션보다 많으면 나머지는 놉니다)
                .replicas(1) // 운영 환경에서는 장애 허용과 고가용성을 위해 3 이상으로 설정, 각 파티션마다 1개의 리더와 2개의 팔로워로 구성됨
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1") // 운영 환경에서는 최소 2개 이상의 리플리카(리더, 팔로워)에 데이터가 복제되었을 때만 쓰기 성공(ack)으로 간주하여 데이터 유실 방지
                .build();
    }

    @Bean
    public NewTopic couponIssueRetryTopic(@Value("${kafka.topic.coupon-issue-retry}") final String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic couponIssueDbDownDqlTopic(@Value("${kafka.topic.coupon-issue}") final String issueTopic) {
        return TopicBuilder.name(issueTopic + "-db-down-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
