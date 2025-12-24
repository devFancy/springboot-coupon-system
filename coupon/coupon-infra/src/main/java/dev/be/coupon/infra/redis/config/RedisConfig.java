package dev.be.coupon.infra.redis.config;

import dev.be.coupon.infra.exception.CouponInfraException;
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

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + redisHost + ":" + redisPort)
                .setConnectionPoolSize(65)
                .setConnectionMinimumIdleSize(12)
                .setConnectTimeout(2000)
                .setTimeout(1000);

        log.info("[RedisConfig] Redisson Client 연결 시도: {}:{}", redisHost, redisPort);
        try {
            return Redisson.create(config);
        } catch (Exception e) {
            log.error("[RedisConfig] Redisson Client 생성 실패. 인프라 설정을 확인해 주세요. host={}", redisHost, e);
            throw new CouponInfraException(" Redis 연결 설정에 실패했습니다.");
        }
    }

    @Primary
    @Bean
    public RedisTemplate<String, String> couponRedisTemplate(final RedisConnectionFactory connectionFactory) {
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
    public RRateLimiter couponConsumerRateLimiter(final RedissonClient redissonClient) {
        log.info("[RedisConfig] RRateLimiter Bean 생성 시도 (RateLimiter Total TPS: {})", totalMaxTps);

        final String RATE_LIMITER = "coupon_issuance_rate_limiter";
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(RATE_LIMITER);

        // NOTE: RateType.OVERALL : 모든 Consumer 인스턴스가 이 TPS를 공유 (필수)
        // totalMaxTps / 1 초 : 1초당 totalMaxTps 만큼의 허가증(토큰)을 생성
        rateLimiter.trySetRate(RateType.OVERALL, totalMaxTps, 1, RateIntervalUnit.SECONDS);

        log.info("[RedisConfig] RRateLimiter Bean 생성 완료");
        return rateLimiter;
    }
}
