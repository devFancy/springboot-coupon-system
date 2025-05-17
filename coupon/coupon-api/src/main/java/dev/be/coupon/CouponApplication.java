package dev.be.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "dev.be.coupon.api",
        "dev.be.coupon.domain",
        "dev.be.coupon.common"
})
@EnableJpaRepositories(basePackages = {
        "dev.be.coupon.api",
        "dev.be.coupon.domain.coupon.infrastructure"
})
@EntityScan(basePackages = {
        "dev.be.coupon.api.auth.domain",
        "dev.be.coupon.api.user.domain",
        "dev.be.coupon.domain.coupon"
})
public class CouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }

}
