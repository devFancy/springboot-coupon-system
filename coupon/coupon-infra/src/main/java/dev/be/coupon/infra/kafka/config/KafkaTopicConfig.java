package dev.be.coupon.infra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic couponIssueTopic() {
        return TopicBuilder.name("coupon_issue")
                .partitions(3)      // 파티션 수
                .replicas(3)        // 운영 환경에서는 장애 허용과 고가용성을 위해 3 이상으로 설정
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2") // 최소 2개 이상의 리플리카에 데이터가 복제되었을 때만 쓰기 성공으로 간주하여 데이터 유실 방지
                .build();
    }
}
