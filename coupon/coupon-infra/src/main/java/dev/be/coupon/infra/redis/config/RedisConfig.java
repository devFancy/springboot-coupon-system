package dev.be.coupon.infra.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.be.coupon.domain.coupon.Coupon;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedissonClient Configuration
 * RedisTemplate
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);


    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private static final String REDISSON_HOST_PREFIX = "redis://";

    @Bean(destroyMethod = "shutdown") // 애플리케이션 종료 시 RedissonClient 자원 해제
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + redisHost + ":" + redisPort)
                .setConnectionPoolSize(128)
                .setConnectionMinimumIdleSize(24)
                .setConnectTimeout(10000)
                .setTimeout(5000);

        log.info("Redisson Client 생성 시도: {}{}:{}", REDISSON_HOST_PREFIX, redisHost, redisPort);
        try {
            RedissonClient redisson = Redisson.create(config);
            log.info("Redisson Client 생성 성공");
            return redisson;
        } catch (Exception e) {
            log.error("Redisson Client 생성 실패", e);
            throw e;
        }
    }

    /**
     * V1: Coupon 객체 전체를 직렬화하는 템플릿
     */
    @Bean("couponV1RedisTemplate")
    public RedisTemplate<String, Coupon> couponRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Coupon> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // LocalDateTime 대응
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();

        log.info("[V1] couponRedisTemplate 빈 생성 완료");
        return template;
    }

    /**
     * V2: 모든 데이터를 String으로 다루는 템플릿
     */
    @Bean("couponV2RedisTemplate")
    public RedisTemplate<String, String> couponV2RedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("[V2] couponV2RedisTemplate 빈 생성 완료");
        return template;
    }
}
