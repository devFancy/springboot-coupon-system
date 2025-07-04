package dev.be.coupon.api.coupon.presentation.v1;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.coupon.application.v1.SyncCouponServiceImpl;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponUsageResponse;
import dev.be.coupon.common.support.response.CommonResponse;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import org.springframework.http.HttpStatus;
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
public class CouponV1Controller implements CouponV1ControllerDocs {

    private final SyncCouponServiceImpl couponServiceImpl;

    public CouponV1Controller(final SyncCouponServiceImpl couponServiceImpl) {
        this.couponServiceImpl = couponServiceImpl;
    }

    @PostMapping(value = "/coupon/")
    public ResponseEntity<CommonResponse<CouponCreateResponse>> create(
            @AuthenticationPrincipal final LoginUser loginUser,
            @RequestBody final CouponCreateRequest request) {

        if (loginUser == null || loginUser.id() == null) {
            throw new UnauthorizedAccessException("로그인된 사용자만 쿠폰을 생성할 수 있습니다.");
        }
        CouponCreateResult result = couponServiceImpl.create(request.toCommand(loginUser.id()));
        return ResponseEntity.created(URI.create("/api/coupon/" + result.id()))
                .body(CommonResponse.success(CouponCreateResponse.from(result)));
    }

    @PostMapping(value = "/v1/coupon/{couponId}/issue")
    public ResponseEntity<CommonResponse<CouponIssueResponse>> issue(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId) {

        if (loginUser == null || loginUser.id() == null) {
            throw new UnauthorizedAccessException("로그인된 사용자만 쿠폰을 발급받을 수 있습니다.");
        }

        CouponIssueCommand command = new CouponIssueCommand(loginUser.id(), couponId);
        CouponIssueResult result = couponServiceImpl.issue(command);

        HttpStatus status = resolveStatus(result);
        return ResponseEntity.status(status)
                .body(CommonResponse.success(CouponIssueResponse.from(result)));
    }

    @PostMapping(value = "/v1/coupon/{couponId}/issue/test")
    public ResponseEntity<CommonResponse<CouponIssueResponse>> issue(
            @RequestBody final CouponIssueRequest request,
            @PathVariable final UUID couponId) {

        CouponIssueCommand command = new CouponIssueCommand(request.userId(), couponId);
        CouponIssueResult result = couponServiceImpl.issue(command);

        HttpStatus status = resolveStatus(result);
        return ResponseEntity.status(status)
                .body(CommonResponse.success(CouponIssueResponse.from(result)));
    }

    @PostMapping(value = "/coupon/{couponId}/usage")
    public ResponseEntity<CommonResponse<CouponUsageResponse>> usage(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId) {

        if (loginUser == null || loginUser.id() == null) {
            throw new UnauthorizedAccessException("로그인된 사용자만 쿠폰을 발급받을 수 있습니다.");
        }

        CouponUsageCommand command = new CouponUsageCommand(loginUser.id(), couponId);
        CouponUsageResult result = couponServiceImpl.usage(command);
        return ResponseEntity.ok().body(CommonResponse.success(CouponUsageResponse.from(result)));
    }

    private HttpStatus resolveStatus(final CouponIssueResult result) {
        if (result.alreadyIssued()) {
            return HttpStatus.OK;
        } else if (result.quantityExceeded()) {
            return HttpStatus.CONFLICT;
        } else if (!result.used()) {
            return HttpStatus.CREATED;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
