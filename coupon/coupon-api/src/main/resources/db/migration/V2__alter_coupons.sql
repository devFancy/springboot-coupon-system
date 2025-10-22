alter table coupons
    drop
column valid_from,
    drop
column valid_until,

    add column coupon_discount_type VARCHAR(255) NOT NULL,
    add column coupon_discount_value DECIMAL(19, 2) NOT NULL,

    add column expired_at DATETIME(6) NOT NULL;
