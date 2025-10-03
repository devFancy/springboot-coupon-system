package dev.be.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Spring Cloud Config와 같은 설정 서버를 도입하면, 애플리케이션을 재시작하지 않고도 동적으로 스케줄러를 껐다 켰다 하는 것도 가능함.
@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {
        "dev.be.coupon.api",
        "dev.be.coupon.domain",
        "dev.be.coupon.infra",
        "dev.be.coupon.common",
        "dev.be.coupon.logging"
})
@EnableJpaRepositories(basePackages = {
        "dev.be.coupon.infra.jpa",
})
@EntityScan(basePackages = {
        "dev.be.coupon.domain"
})
public class CouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }

}
