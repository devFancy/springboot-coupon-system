package dev.be.coupon.kafka.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = {
		"dev.be.coupon.kafka.consumer",
		"dev.be.coupon.domain.coupon",
		"dev.be.coupon.infra",
		"dev.be.coupon.logging"
})
@EnableJpaRepositories(basePackages = {
		"dev.be.coupon.infra.jpa"
})
@EntityScan(basePackages = {
		"dev.be.coupon.domain"
})
public class CouponKafkaConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CouponKafkaConsumerApplication.class, args);
	}

}
