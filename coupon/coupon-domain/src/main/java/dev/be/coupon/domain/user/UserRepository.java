package dev.be.coupon.domain.user;

import dev.be.coupon.domain.user.exception.UserDomainException;
import dev.be.coupon.domain.user.vo.Username;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(final User user);

    boolean existsByUsername(final Username username);

    Optional<User> findByUsername(final Username username);

    default void validateExistById(final UUID userId) {
        if (!existsById(userId)) {
            throw new UserDomainException("해당 ID의 사용자가 존재하지 않습니다.");
        }
    }

    boolean existsById(final UUID userId);

    boolean isAdmin(final UUID loginUserId);
}
