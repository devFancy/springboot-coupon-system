package dev.be.coupon.api.user.application;

import dev.be.coupon.api.user.application.dto.UserLoginCommand;
import dev.be.coupon.api.user.application.dto.UserLoginResult;
import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.application.dto.UserSignUpResult;
import dev.be.coupon.api.user.domain.User;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import dev.be.coupon.api.user.domain.vo.PasswordHasher;
import dev.be.coupon.api.user.domain.vo.Username;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(final UserRepository userRepository, final PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public UserSignUpResult signUp(final UserSignUpCommand command) {
        if (userRepository.existsByUsername(new Username(command.username()))) {
            throw new RuntimeException("이미 존재하는 사용자 이름입니다.");
        }

        final User user = new User(command.username(), command.password());
        return UserSignUpResult.from(userRepository.save(user));
    }

    public UserLoginResult login(final UserLoginCommand command) {
        final User savedUser = userRepository.findByUsername(new Username(command.username()));
        if (Objects.isNull(savedUser)) {
            throw new InvalidUserException("존재하지 않는 사용자입니다");
        }

        if (!savedUser.isPasswordMatched(command.password(), passwordHasher)) {
            throw new InvalidUserException("비밀번호가 일치하지 않습니다");
        }
        return UserLoginResult.from(savedUser);
    }
}
