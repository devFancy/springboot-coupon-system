create table coupon_issue_failed_events
(
    id            binary(16)   not null,
    user_id       binary(16)   not null,
    coupon_id     binary(16)   not null,
    payload       text,
    error_message text,
    status        varchar(255) not null,
    created_at    datetime(6)  not null,
    primary key (id)
) engine = InnoDB;

create index idx_coupon_issue_failed_events_status on coupon_issue_failed_events (status);
create index idx_coupon_issue_failed_events_user_id on coupon_issue_failed_events (user_id);

alter table coupon_issue_failed_events
    add constraint fk_coupon_issue_failed_events_to_users
        foreign key (user_id)
            references users (id);

alter table coupon_issue_failed_events
    add constraint fk_coupon_issue_failed_events_to_coupons
        foreign key (coupon_id)
            references coupons (id);
