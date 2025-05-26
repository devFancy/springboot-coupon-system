import http from 'k6/http';
import {check} from 'k6';
import {uuidv4} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import {sleep} from 'k6';

export const options = {
    scenarios: {
        warmup_then_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '60s', target: 1000 },
                { duration: '60s', target: 2000 },
                { duration: '60s', target: 4000 },
                { duration: '60s', target: 6000 },
                { duration: '5m', target: 6000 },
                // { duration: '60s', target: 8000 },
                // { duration: '60s', target: 10000 },
            ],
        },
    },
    thresholds: {
        //http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        http_req_duration: ['p(95)<3000'], // ms 단위
    },
};

const couponId = 'bde3e558-c52a-4534-b4f7-cbab80c6c879'; // 쿠폰 ID

export default function () {
    sleep(2);

    const userId = uuidv4();
    const url = `http://localhost:8080/api/coupon/${couponId}/issue/test`;
    const headers = {'Content-Type': 'application/json'};
    const payload = JSON.stringify({userId});

    const res = http.post(url, payload, {
        headers, timeout: "5s"
    });

    check(res, {
        'issue request success': (r) => [200, 201, 409].includes(r.status),
    });

    try {
        const result = JSON.parse(res.body);
        console.log(`[${userId}] 발급 상태: ${result.resultType}, 쿠폰 ID: ${result.data?.couponId}`);
    } catch (e) {
        console.error(`[${userId}] 응답 파싱 실패: ${res.status}, body: ${res.body}`);
    }
}
