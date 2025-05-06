package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import dev.be.coupon.api.coupon.domain.UserRoleChecker;
import dev.be.coupon.api.coupon.domain.exception.UnauthorizedAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserRoleChecker userRoleChecker;

    public CouponService(final CouponRepository couponRepository,
                         final UserRoleChecker userRoleChecker) {
        this.couponRepository = couponRepository;
        this.userRoleChecker = userRoleChecker;
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
}
