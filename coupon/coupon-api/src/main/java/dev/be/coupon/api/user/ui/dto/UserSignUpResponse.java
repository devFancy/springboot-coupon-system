package dev.be.coupon.api.user.ui.dto;

import java.util.UUID;

public record UserSignUpResponse (UUID id, String username) {
}
