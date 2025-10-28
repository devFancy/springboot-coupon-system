-- `issued_coupons` 테이블에 `coupons` 테이블을 참조하는 외래 키 제약조건을 추가합니다.
-- `coupon_id`를 `coupons` 테이블의 `id`와 연결합니다.
-- ON DELETE RESTRICT: `coupons` 테이블의 원본 쿠폰을 삭제하려 할 때,
--                     `issued_coupons`에 발급된 데이터가 남아있으면 삭제를 막습니다. (데이터 정합성 보호)
ALTER TABLE issued_coupons -- 자식 테이블
    ADD CONSTRAINT fk_issued_coupons_coupon_id
        FOREIGN KEY (coupon_id)
            REFERENCES coupons (id) -- 부모 테이블
            ON DELETE RESTRICT;
