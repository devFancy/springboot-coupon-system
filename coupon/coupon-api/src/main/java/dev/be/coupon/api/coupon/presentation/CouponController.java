package dev.be.coupon.api.coupon.presentation;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.common.support.response.CommonResponse;
import dev.be.coupon.api.coupon.application.CouponService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RequestMapping("/api")
@RestController
public class CouponController implements CouponControllerDocs {

    private final CouponService couponService;

    public CouponController(final CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping(value = "/coupon/")
    public ResponseEntity<CommonResponse<CouponCreateResponse>> create(
            @AuthenticationPrincipal final LoginUser loginUser,
            @RequestBody final CouponCreateRequest request) {

        if (loginUser == null || loginUser.id() == null) {
            throw new UnauthorizedAccessException("로그인된 사용자만 쿠폰을 생성할 수 있습니다.");
        }
        CouponCreateResult result = couponService.create(request.toCommand(loginUser.id()));
        return ResponseEntity.created(URI.create("/api/coupon/" + result.id()))
                .body(CommonResponse.success(CouponCreateResponse.from(result)));
    }

    @PostMapping(value = "/coupon/{couponId}/issue")
    public ResponseEntity<CommonResponse<CouponIssueResponse>> issue(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId) {
        CouponIssueCommand command = new CouponIssueCommand(loginUser.id(), couponId);
        CouponIssueResult result = couponService.issue(command);
        return ResponseEntity.created(URI.create("/api/coupon/" + result.couponId()))
                .body(CommonResponse.success(CouponIssueResponse.from(result)));
    }
}
