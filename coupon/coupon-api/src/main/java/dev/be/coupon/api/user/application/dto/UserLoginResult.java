package dev.be.coupon.api.user.application.dto;

import dev.be.coupon.api.user.domain.User;

import java.util.UUID;

public record UserLoginResult(UUID id, String username) {
    public static UserLoginResult from(final User user) {
        return new UserLoginResult(
                user.getId(),
                user.getUsername()
        );
    }
}
