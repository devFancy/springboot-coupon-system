package dev.be.coupon.infra.redis;

import dev.be.coupon.infra.exception.CouponInfraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponEntryRedisCounterTest {
    @InjectMocks
    private CouponEntryRedisCounter couponEntryRedisCounter;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("최초 진입 시 카운트가 1 증가하고 만료 시간이 설정된다.")
    void should_increment_and_set_expire_on_first_entry() {
        // given
        final UUID couponId = UUID.randomUUID();
        final String key = "coupon:entry_count" + couponId;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(key)).willReturn(1L);

        // when
        final long count = couponEntryRedisCounter.increment(couponId);

        // then
        assertThat(count).isEqualTo(1L);
        verify(redisTemplate, times(1)).expire(key, 3, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("기존 진입자의 경우 카운트만 증가하고 만료 시간은 설정하지 않는다.")
    void should_only_increment_without_setting_expire_on_subsequent_entry() {
        // given
        final UUID couponId = UUID.randomUUID();
        final String key = "coupon:entry_count" + couponId;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(key)).willReturn(2L);

        // when
        long count = couponEntryRedisCounter.increment(couponId);

        // then
        assertThat(count).isEqualTo(2L);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Redis 응답이 null이면 예외가 발생한다.")
    void should_throw_exception_when_redis_response_is_null() {
        // given
        final UUID couponId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment(anyString())).willReturn(null);

        // when & then
        assertThatThrownBy(() -> couponEntryRedisCounter.increment(couponId))
                .isInstanceOf(CouponInfraException.class)
                .hasMessage("Redis 카운팅이 실패했습니다.");
    }
}