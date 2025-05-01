package dev.be.coupon.api.user.presentation.dto;

import java.util.UUID;

public record UserSignUpResponse (UUID id, String username, String role) {
}
