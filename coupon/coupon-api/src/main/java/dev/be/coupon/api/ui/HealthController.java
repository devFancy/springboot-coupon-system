package dev.be.coupon.api.ui;

import dev.be.coupon.api.support.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
public class HealthController implements HealthControllerDocs {

    @GetMapping("/health")
    @Override
    public ResponseEntity<CommonResponse<?>> health() {
        return ResponseEntity.ok(CommonResponse.success());
    }
}
