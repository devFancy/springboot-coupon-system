package dev.be.coupon.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.infra.exception.CouponInfraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponRedisCacheTest {

    @InjectMocks
    private CouponRedisCache couponRedisCache;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("쿠폰 정보를 직렬화하여 Redis에 저장한다.")
    void should_save_coupon_successfully() throws JsonProcessingException {
        // given
        final Coupon coupon = mock(Coupon.class);
        given(coupon.getId()).willReturn(UUID.randomUUID());

        final String key = "coupon:meta" + coupon.getId();
        final String jsonValue = "{\"id\":\"...\"}";

        given(objectMapper.writeValueAsString(coupon)).willReturn(jsonValue);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        couponRedisCache.save(coupon);

        // then
        verify(valueOperations).set(eq(key), eq(jsonValue), any(Duration.class));
    }

    @Test
    @DisplayName("직렬화 실패 시 예외가 발생한다.")
    void should_throw_exception_when_serialization_fails() throws JsonProcessingException {
        // given
        final Coupon coupon = mock(Coupon.class);
        given(coupon.getId()).willReturn(UUID.randomUUID());
        given(objectMapper.writeValueAsString(coupon)).willThrow(JsonProcessingException.class);

        // when & then
        assertThatThrownBy(() -> couponRedisCache.save(coupon))
                .isInstanceOf(CouponInfraException.class)
                .hasMessage("쿠폰 정보를 직렬화하는데 실패했습니다.");
    }

    @Test
    @DisplayName("역직렬화 실패 시 빈 값을 반환하고 해당 키를 삭제한다.")
    void should_return_empty_and_delete_key_when_deserialization_fails() throws JsonProcessingException {
        // given
        final UUID couponId = UUID.randomUUID();
        final String key = "coupon:meta" + couponId;
        final String invalidJson = "invalid";

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(invalidJson);
        given(objectMapper.readValue(invalidJson, Coupon.class)).willThrow(JsonProcessingException.class);

        // when
        Optional<Coupon> result = couponRedisCache.getCouponById(couponId);

        // then
        assertThat(result).isEmpty();
        verify(redisTemplate).delete(key);
    }
}