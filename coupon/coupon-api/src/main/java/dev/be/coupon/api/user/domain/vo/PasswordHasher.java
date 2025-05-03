package dev.be.coupon.api.user.domain.vo;

public interface PasswordHasher {
    String encrypt(final String password);
    boolean matches(final String password, final String encryptedPassword);
}
