package dev.be.coupon.domain.coupon;

import java.util.UUID;

public interface UserRoleChecker {

    boolean isAdmin(final UUID userId);
}
