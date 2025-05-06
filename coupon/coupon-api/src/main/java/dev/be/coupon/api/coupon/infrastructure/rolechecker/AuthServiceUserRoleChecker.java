package dev.be.coupon.api.coupon.infrastructure.rolechecker;

import dev.be.coupon.api.auth.application.AuthService;
import dev.be.coupon.api.coupon.domain.UserRoleChecker;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 쿠폰 도메인에서 관리자 권한 여부를 확인하기 위한 인증 서비스 기반 구현체입니다.
 * - 인증 도메인과의 결합을 줄이기 위해 UserRoleChecker 인터페이스를 통해 의존합니다.
 */
@Component
public class AuthServiceUserRoleChecker implements UserRoleChecker {

    private final AuthService authService;

    public AuthServiceUserRoleChecker(final AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean isAdmin(final UUID userId) {
        return authService.isAdmin(userId);
    }
}
