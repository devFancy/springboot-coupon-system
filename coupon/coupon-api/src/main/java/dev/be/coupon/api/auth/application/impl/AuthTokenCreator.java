package dev.be.coupon.api.auth.application.impl;

import dev.be.coupon.api.auth.application.TokenCreator;
import dev.be.coupon.api.auth.application.TokenProvider;
import dev.be.coupon.domain.auth.AuthAccessToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthTokenCreator implements TokenCreator {

    private final TokenProvider tokenProvider;

    public AuthTokenCreator(final TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public AuthAccessToken createAuthToken(final UUID userId) {
        String accessToken = tokenProvider.createAccessToken(String.valueOf(userId));

        return new AuthAccessToken(accessToken);
    }

    public UUID extractPayLoad(final String accessToken) {
        tokenProvider.validateToken(accessToken);
        return UUID.fromString(tokenProvider.getPayLoad(accessToken));
    }
}
