package dev.be.coupon.api.user.application.dto;

import dev.be.coupon.api.user.domain.User;

import java.util.UUID;

public record UserSignUpResult(UUID id, String username, String role) {
    public static UserSignUpResult from(final User user) {
        return new UserSignUpResult(
                user.getId(),
                user.getUsername(),
                user.getUserRole().name().toLowerCase());
    }
}
