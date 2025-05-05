package dev.be.coupon.api.coupon.presentation;

import dev.be.coupon.api.common.support.response.CommonResponse;
import dev.be.coupon.api.coupon.application.CouponService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RequestMapping("/api")
@RestController
public class CouponController implements CouponControllerDocs {

    private final CouponService couponService;

    public CouponController(final CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping(value = "/coupon/")
    public ResponseEntity<CommonResponse<CouponCreateResponse>> create(
            @RequestBody final CouponCreateRequest request) {

        CouponCreateCommand command = new CouponCreateCommand(
                request.name(), request.type(), request.totalQuantity(), request.validFrom(), request.validUntil());
        CouponCreateResult result = couponService.create(command);
        CouponCreateResponse response = new CouponCreateResponse(
                result.id(), result.name(), result.type(), result.totalQuantity(), result.status(), result.validFrom(), result.validUntil());

        return ResponseEntity.created(URI.create("/api/coupon/" + response.id()))
                .body(CommonResponse.success(response));
    }
}
