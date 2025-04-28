package dev.be.coupon.api.user.application;

import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.application.dto.UserSignUpResult;
import dev.be.coupon.api.user.domain.User;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.vo.Username;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserSignUpResult signUp(final UserSignUpCommand command) {
        if (userRepository.existsByUsername(new Username(command.username()))) {
            throw new RuntimeException("이미 존재하는 사용자 이름입니다.");
        }

        final User user = new User(command.username(), command.password());
        return UserSignUpResult.from(userRepository.save(user));
    }
}
