package dev.be.coupon.api.coupon.application;

import deb.be.coupon.CouponStatus;
import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand;
import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;
import dev.be.coupon.api.coupon.application.exception.CouponNotFoundException;
import dev.be.coupon.api.coupon.application.exception.InvalidIssuedCouponException;
import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.IssuedCoupon;
import dev.be.coupon.api.coupon.domain.IssuedCouponRepository;
import dev.be.coupon.api.coupon.domain.UserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserRoleChecker userRoleChecker;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponService(final CouponRepository couponRepository,
                         final UserRoleChecker userRoleChecker,
                         final IssuedCouponRepository issuedCouponRepository) {
        this.couponRepository = couponRepository;
        this.userRoleChecker = userRoleChecker;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    public CouponCreateResult create(
            final UUID loginUserId,
            final CouponCreateCommand command) {

        if (!userRoleChecker.isAdmin(loginUserId)) {
            throw new UnauthorizedAccessException("쿠폰 생성은 관리자만 가능합니다.");
        }

        final Coupon coupon = command.toDomain();
        return CouponCreateResult.from(couponRepository.save(coupon));
    }

    @Transactional
    public CouponIssueResult issue(final CouponIssueCommand command) {
        final UUID userId = command.userId();
        final UUID couponId = command.couponId();

        final Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("존재하지 않는 쿠폰입니다."));

        coupon.updateStatusBasedOnDate(LocalDateTime.now());
        if (coupon.getCouponStatus() != CouponStatus.ACTIVE) {
            throw new InvalidCouponException("현재 쿠폰은 발급 가능한 상태가 아닙니다.");
        }

        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new InvalidIssuedCouponException("이미 해당 쿠폰을 발급받았습니다.");
        }

        int issuedCount = issuedCouponRepository.countByCouponId(couponId);
        if (issuedCount >= coupon.getTotalQuantity()) {
            throw new InvalidIssuedCouponException("해당 쿠폰의 발급 수량이 초과되었습니다.");
        }

        final IssuedCoupon issuedCoupon = new IssuedCoupon(userId, couponId);
        issuedCouponRepository.save(issuedCoupon);

        return CouponIssueResult.from(issuedCoupon);
    }
}
