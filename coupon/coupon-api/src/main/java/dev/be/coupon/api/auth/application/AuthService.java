package dev.be.coupon.api.auth.application;


import dev.be.coupon.api.auth.application.dto.AuthLoginCommand;
import dev.be.coupon.api.auth.application.dto.AuthLoginResult;
import dev.be.coupon.domain.auth.AuthAccessToken;
import dev.be.coupon.domain.user.User;
import dev.be.coupon.domain.user.UserRepository;
import dev.be.coupon.domain.user.exception.InvalidUserException;
import dev.be.coupon.domain.user.vo.PasswordHasher;
import dev.be.coupon.domain.user.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenCreator tokenCreator;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       TokenCreator tokenCreator) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenCreator = tokenCreator;
    }

    public AuthLoginResult login(final AuthLoginCommand command) {
        final User savedUser = findUserByUsername(new Username(command.username()));

        if (!savedUser.isPasswordMatched(command.password(), passwordHasher)) {
            throw new InvalidUserException("비밀번호가 일치하지 않습니다");
        }

        return AuthLoginResult.from(savedUser);  // accessToken 나중에 추가
    }


    @Transactional(readOnly = true)
    public User findUserByUsername(final Username username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidUserException("존재하지 않는 사용자입니다"));
    }

    public String generateAccessToken(final UUID userId) {
        AuthAccessToken authAccessToken = tokenCreator.createAuthToken(userId);
        return authAccessToken.getAccessToken();
    }

    public UUID extractUserId(final String accessToken) {
        UUID userId = tokenCreator.extractPayLoad(accessToken); // 내부적으로 JWT decode
        userRepository.validateExistById(userId); // 유효 사용자인지 확인
        return userId;
    }

    public boolean isAdmin(final UUID userId) {
        return userRepository.isAdmin(userId);
    }
}
