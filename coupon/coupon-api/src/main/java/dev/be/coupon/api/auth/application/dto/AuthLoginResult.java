package dev.be.coupon.api.auth.application.dto;

import dev.be.coupon.domain.user.User;

import java.util.UUID;

public record AuthLoginResult(UUID id, String username, String accessToken) {

    public static AuthLoginResult from(final User user) {
        return new AuthLoginResult(user.getId(), user.getUsername(), null);
    }

    public AuthLoginResult withAccessToken(final String token) {
        return new AuthLoginResult(this.id, this.username, token);
    }
}
