package dev.be.coupon.api.coupon.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
}
