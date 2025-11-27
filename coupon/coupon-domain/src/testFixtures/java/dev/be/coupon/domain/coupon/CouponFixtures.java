package dev.be.coupon.domain.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponFixtures {

    public static final String 쿠폰_이름 = "정상 쿠폰";
    public static final CouponType 쿠폰_타입_버거 = CouponType.BURGER;
    public static final CouponDiscountType 쿠폰_할인_유형 = CouponDiscountType.FIXED;
    public static final BigDecimal 쿠폰_할인_금액 = BigDecimal.valueOf(10_000L);
    public static final int 쿠폰_총_발급_수량 = 100;
    public static final LocalDateTime 유효_기간일 = LocalDateTime.now().plusDays(7);

    public static Coupon 정상_쿠폰() {
        return new Coupon(
                쿠폰_이름,
                쿠폰_타입_버거,
                쿠폰_할인_유형,
                쿠폰_할인_금액,
                쿠폰_총_발급_수량,
                유효_기간일
        );
    }

    public static Coupon 정상_쿠폰(int totalQuantity) {
        return new Coupon(
                쿠폰_이름,
                쿠폰_타입_버거,
                쿠폰_할인_유형,
                쿠폰_할인_금액,
                totalQuantity,
                유효_기간일
        );
    }

    public static Coupon 쿠폰_이름이_존재하지_않음() {
        return new Coupon(
                null,
                쿠폰_타입_버거,
                쿠폰_할인_유형,
                쿠폰_할인_금액,
                쿠폰_총_발급_수량,
                유효_기간일
        );
    }

    public static Coupon 쿠폰_할인_유형이_존재하지_않음() {
        return new Coupon(
                쿠폰_이름,
                쿠폰_타입_버거,
                null,
                쿠폰_할인_금액,
                쿠폰_총_발급_수량,
                유효_기간일
        );
    }

    public static Coupon 쿠폰_총_발급_수량이_0보다_작은경우() {
        return new Coupon(
                쿠폰_이름,
                쿠폰_타입_버거,
                쿠폰_할인_유형,
                쿠폰_할인_금액,
                0,
                유효_기간일
        );
    }

    public static Coupon 만료된_쿠폰() {
        LocalDateTime 만료된_유효_기간일 = LocalDateTime.now().minusDays(1);
        return new Coupon(
                쿠폰_이름,
                쿠폰_타입_버거,
                쿠폰_할인_유형,
                쿠폰_할인_금액,
                쿠폰_총_발급_수량,
                만료된_유효_기간일
        );
    }
}
