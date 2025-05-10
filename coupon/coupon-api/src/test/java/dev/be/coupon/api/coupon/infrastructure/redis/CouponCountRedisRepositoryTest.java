package dev.be.coupon.api.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

class CouponCountRedisRepositoryTest {

    @DisplayName("increment()를 호출하면 Redis에서 coupon_count 값을 1 증가시킨다.")
    @Test
    void increment_should_increase_coupon_count() {
        // given
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("coupon_count:abc123")).thenReturn(1L);

        CouponCountRedisRepository repository = new CouponCountRedisRepository(redisTemplate);

        // when
        Long result = repository.increment("coupon_count:abc123");

        // then
        assertThat(result).isEqualTo(1L);
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).increment("coupon_count:abc123");
    }

    // 사용자 단위 중복 발급 방지를 위한 분산 락 획득 로직을 검증하는 테스트
    @DisplayName("tryLock()이 Redis에 정상적으로 락을 설정하면 true를 반환한다.")
    @Test
    void tryLock_should_return_true_if_lock_acquired() {
        // given
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("lock:coupon:xyz", "locked", Duration.ofSeconds(3))).thenReturn(true);

        CouponCountRedisRepository repository = new CouponCountRedisRepository(redisTemplate);

        // when
        boolean result = repository.tryLock("lock:coupon:xyz", 3);

        // then
        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent("lock:coupon:xyz", "locked", Duration.ofSeconds(3));
    }

    @DisplayName("releaseLock()은 Redis에서 주어진 key를 삭제한다.")
    @Test
    void releaseLock_should_delete_key_from_redis() {
        // given
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        CouponCountRedisRepository repository = new CouponCountRedisRepository(redisTemplate);

        // when
        repository.releaseLock("lock:coupon:xyz");

        // then
        verify(redisTemplate).delete("lock:coupon:xyz");
    }
}
