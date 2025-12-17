package dev.be.coupon.infra.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final String REDISSON_HOST_PREFIX = "redis://";

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${redisson.rate-limiter.coupon-issue.tps:100}")
    private long totalMaxTps;

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean(destroyMethod = "shutdown") // 애플리케이션 종료 시 RedissonClient 자원 해제
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + redisHost + ":" + redisPort)
                .setConnectionPoolSize(128)
                .setConnectionMinimumIdleSize(24)
                .setConnectTimeout(10000)
                .setTimeout(5000);

        log.info("[RedisConfig] Redisson Client 생성 시도: {}{}:{}", REDISSON_HOST_PREFIX, redisHost, redisPort);
        try {
            RedissonClient redisson = Redisson.create(config);
            log.info("[RedisConfig] Redisson Client 생성 성공");
            return redisson;
        } catch (Exception e) {
            log.error("[RedisConfig] Redisson Client 생성 실패", e);
            throw e;
        }
    }

    @Primary
    @Bean
    public RedisTemplate<String, String> couponRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("[RedisConfig] couponRedisTemplate 빈 생성 완료");
        return template;
    }

    @Bean
    public RRateLimiter couponConsumerRateLimiter(RedissonClient redissonClient) {
        log.info("[RedisConfig] RRateLimiter Bean 생성 시도 (Total TPS: {})", totalMaxTps);

        // NOTE: 모든 Consumer가 공유할 Redis 키 이름
        final String RATE_LIMITER = "coupon_issuance_rate_limiter";
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(RATE_LIMITER);

        // NOTE: RateType.OVERALL : 모든 Consumer 인스턴스가 이 TPS를 공유 (필수)
        // totalMaxTps / 1 초 : 1초당 totalMaxTps 만큼의 허가증(토큰)을 생성
        rateLimiter.trySetRate(RateType.OVERALL, totalMaxTps, 1, RateIntervalUnit.SECONDS);

        log.info("[RedisConfig] RRateLimiter Bean 생성 완료");
        return rateLimiter;
    }
}
