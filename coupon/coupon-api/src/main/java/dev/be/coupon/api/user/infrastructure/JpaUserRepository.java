package dev.be.coupon.api.user.infrastructure;

import dev.be.coupon.api.user.domain.User;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.vo.Username;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaUserRepository extends UserRepository, JpaRepository<User, UUID> {
    boolean existsByUsername(final Username username);

    User findByUsername(final Username username);
}
