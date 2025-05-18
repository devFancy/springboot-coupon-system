package dev.be.coupon.api.user.infrastructure;

import dev.be.coupon.domain.user.vo.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(final PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encrypt(final String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean matches(final String password, final String encryptedPassword) {
        return passwordEncoder.matches(password, encryptedPassword);
    }
}
