package dev.be.coupon.api.user.presentation.dto;

import java.util.UUID;

public record UserLoginResponse(UUID id, String username) {
}
