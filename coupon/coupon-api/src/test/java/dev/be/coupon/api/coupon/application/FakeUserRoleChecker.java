package dev.be.coupon.api.coupon.application;

import dev.be.coupon.domain.coupon.UserRoleChecker;

import java.util.UUID;

public class FakeUserRoleChecker implements UserRoleChecker {

    private boolean isAdmin;

    public void updateIsAdmin(final boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    @Override
    public boolean isAdmin(final UUID userId) {
        return isAdmin;
    }
}
