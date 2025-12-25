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
    private CouponRedisDuplicateValidate couponRedisDuplicateValidate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    @DisplayName("최초 사용자가 쿠폰을 요청하면 Redis Set에 저장하고 true를 반환한다.")
    void should_return_true_when_first_user_requests_coupon() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(key, userId.toString())).willReturn(1L); // 1L: 신규 추가 성공

        // when
        Boolean result = couponRedisDuplicateValidate.isFirstUser(couponId, userId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이미 참여한 사용자가 재요청하면 false를 반환한다.")
    void should_return_false_when_duplicate_user_requests_coupon() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(key, userId.toString())).willReturn(0L); // 0L: 이미 존재함

        // when
        Boolean result = couponRedisDuplicateValidate.isFirstUser(couponId, userId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("보상 로직 수행 시 Redis Set에서 사용자 기록을 정상적으로 삭제한다.")
    void should_remove_user_from_set_successfully() {
        // given
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "coupon:participation_set:" + couponId;

        given(redisTemplate.opsForSet()).willReturn(setOperations);

        // when
        couponRedisDuplicateValidate.remove(couponId, userId);

        // then
        verify(setOperations).remove(key, userId.toString());
    }
}