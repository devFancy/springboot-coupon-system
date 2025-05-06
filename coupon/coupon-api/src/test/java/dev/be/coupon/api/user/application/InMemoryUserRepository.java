package dev.be.coupon.api.user.application;

import deb.be.coupon.UserRole;
import dev.be.coupon.api.user.domain.User;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.vo.Username;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory 기반의 테스트용 UserRepository 구현체입니다.
 * 테스트 환경에서 실제 DB 없이 사용자 데이터를 임시 저장하기 위해 사용됩니다.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> users = new HashMap<>();

    @Override
    public User save(final User user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public boolean existsByUsername(final Username username) {
        return users.values()
                .stream()
                .anyMatch(user -> user.getUsername().equals(username.getUsername()));
    }

    @Override
    public Optional<User> findByUsername(final Username username) {
        return users.values()
                .stream()
                .filter(user -> user.getUsername().equals(username.getUsername()))
                .findFirst();
    }

    @Override
    public void validateExistById(final UUID userId) {
        UserRepository.super.validateExistById(userId);
    }

    @Override
    public boolean existsById(final UUID userId) {
        return users.containsKey(userId);
    }

    @Override
    public boolean isAdmin(final UUID loginUserId) {
        return users.containsKey(loginUserId)
                && users.get(loginUserId).getUserRole() == UserRole.ADMIN;
    }
}
