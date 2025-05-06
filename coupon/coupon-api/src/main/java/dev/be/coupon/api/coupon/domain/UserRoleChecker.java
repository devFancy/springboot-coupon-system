package dev.be.coupon.api.coupon.domain;

import java.util.UUID;

public interface UserRoleChecker {

    boolean isAdmin(final UUID userId);
}
