package dev.be.coupon.domain.user.vo;

public interface PasswordHasher {
    String encrypt(final String password);
    boolean matches(final String password, final String encryptedPassword);
}
