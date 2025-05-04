package dev.be.coupon.api.auth.presentation.dto.response;

import java.util.UUID;

public record AuthLoginResponse(UUID id, String username, String accessToken) {
}
