import http from 'k6/http';
import {check} from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
};

export function setup() {
    // 관리자 로그인
    const loginPayload = JSON.stringify({
        username: 'admin',
        password: 'admin1234',
    });

    const loginHeaders = {'Content-Type': 'application/json'};

    // API Server url (e.g. localhost -> 192.168.x.x)
    const loginRes = http.post('http://localhost:8080/api/auth/login', loginPayload, {
        headers: loginHeaders,
    });

    console.log(`Login status: ${loginRes.status}`);
    console.log(`Login body: ${loginRes.body}`);

    check(loginRes, {
        'login success': (res) => res.status === 200,
    });

    const parsed = JSON.parse(loginRes.body);
    const token = parsed.data.accessToken;

    return {token};
}

export default function (data) {
    const token = data.token;

    const createPayload = JSON.stringify({
        couponName: "치킨 5000원 할인 쿠폰",
        couponType: "CHICKEN",
        couponDiscountType: "FIXED",
        couponDiscountValue: 5000,
        totalQuantity: 500,
        expiredAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), // 7일 후
    });

    const createHeaders = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
    };

    // API Server url (e.g. localhost -> 192.168.x.x)
    const createRes = http.post('http://localhost:8080/api/coupon', createPayload, {
        headers: createHeaders,
    });

    check(createRes, {
        'coupon created': (res) => res.status === 201 || res.status === 200,
    });

    const parsed = JSON.parse(createRes.body);
    console.log(`쿠폰 생성 응답: ${JSON.stringify(parsed, null, 2)}`);

    const couponId = parsed.data?.id;
    console.log(`쿠폰 생성 완료: couponId = ${couponId}`);
}
