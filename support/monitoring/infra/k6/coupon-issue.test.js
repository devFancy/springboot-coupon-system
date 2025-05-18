import http from 'k6/http';
import {check} from 'k6';
import {uuidv4} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { sleep } from 'k6';

export const options = {
    scenarios: {
        constant_requesters: {
            executor: 'constant-vus',
            vus: 1000,
            duration: '100s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<1000'],
    },
};

const couponId = '4f3db4eb-21d3-44db-abca-7ac43df1ab55'; // 쿠폰 ID

export default function () {
    sleep(2);

    const userId = uuidv4();
    const url = `http://localhost:8080/api/coupon/${couponId}/issue/test?userId=${userId}`;
    const headers = {'Content-Type': 'application/json'};

    const res = http.post(url, null, {headers});

    check(res, {
        'issue request success': (r) => r.status === 200 || r.status === 201,
    });

    try {
        const result = JSON.parse(res.body);
        console.log(`[${userId}] 발급 상태: ${result.resultType}, 쿠폰 ID: ${result.data?.couponId}`);
    } catch (e) {
        console.error(`[${userId}] 응답 파싱 실패: ${res.status}, body: ${res.body}`);
    }
}
