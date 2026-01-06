import http from 'k6/http';
import {check} from 'k6';
import {uuidv4} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import {sleep} from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 }, // 이때 DB를 껐다 킨다.
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        //http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

const couponId = '255e9d50-7054-4876-b73c-c19083aeba4e'; // 쿠폰 ID

export default function () {
    sleep(1);

    const userId = uuidv4();
    const globalTraceId = `gtxid-k6-test-${uuidv4()}`;

    // API Server url (e.g. localhost -> 192.168.x.x)
    const url = `http://{API_서버_IP}:{API_서버_PORT}/api/coupon/${couponId}/issue/test`;
    const headers = {
        'Content-Type': 'application/json',
        'X-Global-Trace-Id': globalTraceId
    };
    const payload = JSON.stringify({userId});

    const res = http.post(url, payload, {
        headers,
        timeout: "60s"
    });

    // 성공 시: 202 (Accepted - Kafka 전송됨)
    // 실패(재고 소진/중복): 200 (OK - 안내 메시지 리턴)
    check(res, {
        'issue request success': (r) => [200, 202].includes(r.status),
    });
}
