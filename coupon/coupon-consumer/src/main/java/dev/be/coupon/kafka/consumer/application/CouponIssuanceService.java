package dev.be.coupon.kafka.consumer.application;

import dev.be.coupon.infra.kafka.dto.CouponIssueMessage;

public interface CouponIssuanceService {

    void process(final CouponIssueMessage message);
}
