package dev.be.coupon.api.user.domain;

import dev.be.coupon.api.user.domain.vo.Username;

public interface UserRepository {

    User save(final User user);

    boolean existsByUsername(final Username username);
}
