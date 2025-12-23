package dev.be.coupon.kafka.consumer;

import dev.be.coupon.kafka.consumer.support.response.ApiResultResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/")
@RestController
public class HealthController {
    @GetMapping("/health")
    ResponseEntity<ApiResultResponse> health() {
        return ResponseEntity.ok().body(ApiResultResponse.success());
    }
}

