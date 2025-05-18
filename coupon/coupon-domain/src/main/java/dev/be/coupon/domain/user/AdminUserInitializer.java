package dev.be.coupon.domain.user;

import dev.be.coupon.domain.user.vo.PasswordHasher;
import dev.be.coupon.domain.user.vo.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AdminUserInitializer(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        String adminUsername = "admin";
        String adminPassword = "admin1234";

        boolean exists = userRepository.existsByUsername(new Username(adminUsername));
        if (!exists) {
            User admin = new User(adminUsername, adminPassword, passwordHasher);
            admin.assignAdminRole();
            userRepository.save(admin);
            log.info("관리자 계정이 초기화되었습니다.");
        } else {
            log.warn("관리자 계정이 이미 존재합니다.");
        }
    }
}
