package dev.be.coupon.api.coupon.application;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;
import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;
import dev.be.coupon.api.coupon.domain.Coupon;
import dev.be.coupon.api.coupon.domain.CouponRepository;
import org.springframework.stereotype.Service;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(final CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public CouponCreateResult create(final CouponCreateCommand command) {
        final Coupon coupon= new Coupon(
                command.name(), CouponTypeConverter.from(command.type()), command.totalQuantity(), command.validFrom(), command.validUntil());

        return CouponCreateResult.from(couponRepository.save(coupon));
    }
}
