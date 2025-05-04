package dev.be.coupon.api.user.domain;

import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import dev.be.coupon.api.user.domain.vo.Username;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(final User user);

    boolean existsByUsername(final Username username);

    Optional<User> findByUsername(final Username username);

    default void validateExistById(final UUID userId) {
        if (!existsById(userId)) {
            throw new InvalidUserException("해당 ID의 사용자가 존재하지 않습니다.");
        }
    }

    boolean existsById(UUID userId);
}
