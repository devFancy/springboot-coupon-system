package dev.be.coupon.infra.redis.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class DistributedLockNotAcquiredException extends CouponException {

    public DistributedLockNotAcquiredException(final Object data) {
        super(ErrorType.DISTRIBUTED_LOCK_NOT_ACQUIRED, data);
    }
}
