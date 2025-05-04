package dev.be.coupon.api.auth.application;


import dev.be.coupon.api.auth.domain.AuthAccessToken;

import java.util.UUID;

public interface TokenCreator {
    AuthAccessToken createAuthToken(final UUID userId);

    UUID extractPayLoad(final String accessToken);
}
