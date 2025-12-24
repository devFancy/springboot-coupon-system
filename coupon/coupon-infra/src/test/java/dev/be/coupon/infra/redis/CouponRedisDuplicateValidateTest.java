package dev.be.coupon.infra.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponRedisDuplicateValidateTest {

    @InjectMocks
    private CouponRedisDuplicateValidate sut;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    @DisplayName("[Success] 최초 사용자는 Set에 추가되고 true를 반환한다.")
    void isFirstUser_success_first() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(key, userId.toString())).willReturn(1L);

        // when
        Boolean result = sut.isFirstUser(couponId, userId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[Success] 이미 존재하는 사용자는 false를 반환한다.")
    void isFirstUser_success_duplicate() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(key, userId.toString())).willReturn(0L);

        // when
        Boolean result = sut.isFirstUser(couponId, userId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("[Success] 사용자를 Set에서 제거한다.")
    void remove_success() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);

        // when
        sut.remove(couponId, userId);

        // then
        verify(setOperations).remove(key, userId.toString());
    }
}