package dev.be.coupon.api.auth.presentation.dto;

import java.util.UUID;

public record LoginUser(UUID id) {
    private static final String ADMIN = "ADMIN";
    public boolean hasRole(final String userRoleName) {
        return (userRoleName.equalsIgnoreCase(ADMIN));
    }
}
