package dev.be.coupon.infra.jpa;

import dev.be.coupon.domain.user.UserRole;
import dev.be.coupon.domain.user.User;
import dev.be.coupon.domain.user.UserRepository;
import dev.be.coupon.domain.user.vo.Username;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends UserRepository, JpaRepository<User, UUID> {
    @Override
    User save(final User user);

    @Override
    boolean existsByUsername(final Username username);

    @Override
    Optional<User> findByUsername(final Username username);

    @Override
    boolean existsById(UUID userId);

    @Override
    default boolean isAdmin(final UUID userId) {
        return findById(userId)
                .map(user -> user.getUserRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
