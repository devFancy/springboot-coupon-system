package dev.be.coupon.api.coupon.presentation.v2;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.v2.AsyncCouponService;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueRequest;
import dev.be.coupon.common.support.response.CommonResponse;
import dev.be.coupon.domain.coupon.CouponIssueRequestResult;
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api")
@RestController
public class CouponV2Controller implements CouponV2ControllerDocs {

    private final AsyncCouponService asyncCouponService;

    public CouponV2Controller(final AsyncCouponService asyncCouponService) {
        this.asyncCouponService = asyncCouponService;
    }

    @Override
    @PostMapping(value = "/v2/coupon/{couponId}/issue")
    public ResponseEntity<CommonResponse<String>> issue(
            @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable("couponId") final UUID couponId) {

        if (loginUser == null || loginUser.id() == null) {
            throw new UnauthorizedAccessException("로그인된 사용자만 쿠폰을 발급받을 수 있습니다.");
        }

        CouponIssueCommand command = new CouponIssueCommand(loginUser.id(), couponId);
        return getCommonResponseResponseEntity(command);
    }

    @PostMapping(value = "/v2/coupon/{couponId}/issue/test")
    public ResponseEntity<CommonResponse<String>> issue(
            @RequestBody final CouponIssueRequest request,
            @PathVariable("couponId") final UUID couponId) {

        CouponIssueCommand command = new CouponIssueCommand(request.userId(), couponId);
        return getCommonResponseResponseEntity(command);
    }

    private ResponseEntity<CommonResponse<String>> getCommonResponseResponseEntity(CouponIssueCommand command) {
        CouponIssueRequestResult result = asyncCouponService.issue(command);

        return switch (result) {
            case SUCCESS -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success("쿠폰 발급 요청이 성공적으로 접수되었습니다."));
            case SOLD_OUT -> ResponseEntity.status(HttpStatus.OK)
                    .body(CommonResponse.success("아쉽지만, 쿠폰이 모두 소진되었습니다."));
            case DUPLICATE -> ResponseEntity.status(HttpStatus.OK)
                    .body(CommonResponse.success("이미 참여하셨습니다."));
        };
    }
}
