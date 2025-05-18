package dev.be.coupon.api.auth.application;

import dev.be.coupon.domain.auth.AuthAccessToken;

import java.util.UUID;

public class FakeTokenCreator implements TokenCreator {

    // "token:{UUID}" 형태로 구성하고 다시 파싱
    @Override
    public AuthAccessToken createAuthToken(final UUID userId) {
        return new AuthAccessToken("token:" + userId);
    }

    @Override
    public UUID extractPayLoad(final String accessToken) {
        // e.g "token:44f6a8e0-6e8d-4c68-891a-3d801cc3c7fc"
        if (!accessToken.startsWith("token:")) {
            throw new IllegalArgumentException("잘못된 테스트용 토큰입니다.");
        }
        return UUID.fromString(accessToken.substring("token:".length()));
    }
}
