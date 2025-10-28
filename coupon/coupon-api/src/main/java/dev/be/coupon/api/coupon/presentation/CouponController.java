package dev.be.coupon.api.coupon.presentation;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.coupon.application.CouponService;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand;
import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;
import dev.be.coupon.api.coupon.application.dto.OwnedCouponFindResult;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponUsageResponse;
import dev.be.coupon.api.coupon.presentation.dto.OwnedCouponResponse;
import dev.be.coupon.api.support.error.AuthException;
import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;
import dev.be.coupon.api.support.response.ApiResultResponse;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequestMapping("/api/coupon")
@RestController
public class CouponController implements CouponControllerDocs {

    private final CouponService couponService;

    public CouponController(final CouponService couponService) {
        this.couponService = couponService;
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResultResponse<CouponCreateResponse>> create(
            @AuthenticationPrincipal final LoginUser loginUser,
            @RequestBody final CouponCreateRequest request) {

        if (loginUser == null || loginUser.id() == null) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }

        CouponCreateCommand command = new CouponCreateCommand(
                loginUser.id(),
                request.couponName(),
                request.couponType(),
                request.couponDiscountType(),
                request.couponDiscountValue(),
                request.totalQuantity(),
                request.expiredAt()
        );

        CouponCreateResult result = couponService.create(command);
        return ResponseEntity.created(URI.create("/api/coupon/" + result.id()))
                .body(ApiResultResponse.success(CouponCreateResponse.from(result)));
    }

    @Override
    @PostMapping(value = "/{couponId}/issue")
    public ResponseEntity<ApiResultResponse<String>> issue(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable("couponId") final UUID couponId) {

        if (loginUser == null || loginUser.id() == null) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }

        CouponIssueCommand command = new CouponIssueCommand(loginUser.id(), couponId);
        return getCommonResponseResponseEntity(command);
    }

    @PostMapping(value = "/{couponId}/issue/test")
    public ResponseEntity<ApiResultResponse<String>> issue(
            @RequestBody final CouponIssueRequest request,
            @PathVariable("couponId") final UUID couponId) {

        CouponIssueCommand command = new CouponIssueCommand(request.userId(), couponId);
        return getCommonResponseResponseEntity(command);
    }

    @Override
    @GetMapping(value = "/owned-coupons")
    public ResponseEntity<ApiResultResponse<List<OwnedCouponResponse>>> getOwnedCoupons(
            @AuthenticationPrincipal final LoginUser loginUser) {

        if (loginUser == null || loginUser.id() == null) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }

        List<OwnedCouponFindResult> findResults = couponService.getOwnedCoupons(loginUser.id());
        List<OwnedCouponResponse> responses = findResults.stream()
                .map(OwnedCouponResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok().body(ApiResultResponse.success(responses));
    }

    @Override
    @PostMapping(value = "/{couponId}/usage")
    public ResponseEntity<ApiResultResponse<CouponUsageResponse>> usage(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId) {

        if (loginUser == null || loginUser.id() == null) {
            throw new AuthException(ErrorType.AUTH_ACCESS_DENIED);
        }

        CouponUsageCommand command = new CouponUsageCommand(loginUser.id(), couponId);
        CouponUsageResult result = couponService.usage(command);
        return ResponseEntity.ok().body(ApiResultResponse.success(CouponUsageResponse.from(result)));
    }

    private ResponseEntity<ApiResultResponse<String>> getCommonResponseResponseEntity(CouponIssueCommand command) {
        CouponIssueRequestResult result = couponService.issue(command);

        return switch (result) {
            case SUCCESS -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResultResponse.success("쿠폰 발급 요청이 성공적으로 접수되었습니다. 잠시 후 쿠폰함에서 확인해주세요."));
            case SOLD_OUT -> ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResultResponse.success("아쉽지만, 쿠폰이 모두 소진되었습니다."));
            case DUPLICATE -> ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResultResponse.success("이미 참여하셨습니다."));
        };
    }

    @Override
    @GetMapping("/sentry-test")
    public ResponseEntity<ApiResultResponse<Void>> sentryTest() {
        throw new CouponException(ErrorType.SENTRY_ERROR);
    }
}
