create table coupons
(
    id             binary(16) not null,
    coupon_type    varchar(255) not null,
    name           varchar(255) not null,
    total_quantity integer      not null,
    coupon_status  varchar(255) not null,
    valid_from     datetime(6) not null,
    valid_until    datetime(6) not null,
    primary key (id)
) engine = InnoDB;

create table issued_coupons
(
    id        binary(16) not null,
    user_id   binary(16) not null,
    coupon_id binary(16) not null,
    used      bit not null,
    issued_at datetime(6) not null,
    used_at   datetime(6),
    primary key (id),
    unique (user_id, coupon_id)
) engine = InnoDB;

create table failed_issued_coupons
(
    id          binary(16) not null,
    user_id     binary(16) not null,
    coupon_id   binary(16) not null,
    failed_at   datetime(6) not null,
    retry_count integer not null,
    is_resolved bit     not null,
    primary key (id)
) engine = InnoDB;

create table users
(
    id         binary(16) not null,
    user_role  varchar(255) not null,
    username   varchar(255) not null,
    password   varchar(255) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine = InnoDB;

alter table issued_coupons
    add constraint fk_issued_coupon_to_coupon
        foreign key (coupon_id)
            references coupons (id);

alter table issued_coupons
    add constraint fk_issued_coupon_to_users
        foreign key (user_id)
            references users (id);

alter table failed_issued_coupons
    add constraint fk_failed_issued_coupon_to_users
        foreign key (user_id)
            references users (id);

alter table failed_issued_coupons
    add constraint fk_failed_issued_coupon_to_coupon
        foreign key (coupon_id)
            references coupons (id);
