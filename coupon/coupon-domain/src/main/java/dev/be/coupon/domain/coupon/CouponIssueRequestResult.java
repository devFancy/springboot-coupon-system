package dev.be.coupon.domain.coupon;

public enum CouponIssueRequestResult {
    SUCCESS, // 선착순 성공 (대기열 등록 완료)
    SOLD_OUT, // 선착순 마감
    DUPLICATE // 중복 요청
}
