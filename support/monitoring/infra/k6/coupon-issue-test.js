import http from 'k6/http';
import {check} from 'k6';
import {uuidv4} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import {sleep} from 'k6';

// 아래 options 두개 중 하나만 설정하고 나머지 하나는 주석 처리해줘야 함.
// export const options = {
//     vus: 1,
//     iterations: 1,
// };

export const options = {
    ext: {
        influxdb: {
            // k6가 InfluxDB로 데이터를 보내는 주기를 5초로 설정합니다.
            pushInterval: '5s',
        },
    },
    scenarios: {
        warmup_then_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '1m', target: 1000},
                // {duration: '9m', target: 1000},
                {duration: '1m', target: 2000},
                {duration: '1m', target: 3000},
                {duration: '1m', target: 4000},
                {duration: '1m', target: 5000},
                // {duration: '1m', target: 6000},
                // { duration: '1m', target: 8000 },
                // { duration: '2m', target: 10000 },
                // { duration: '5m', target: 10000 },
            ],
        },
    },
    thresholds: {
        //http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        http_req_duration: ['p(95)<10000'], // ms 단위
    },
};

const couponId = '09b23694-7ea6-4c2f-83e7-db1bb6f7a16c'; // 쿠폰 ID

export default function () {
    sleep(1);

    const userId = uuidv4();
    const globalTraceId = `gtxid-k6-test-${uuidv4()}`;
    // API Server url (e.g. localhost -> 192.168.x.x)
    const url = `http://localhost:8080/api/coupon/${couponId}/issue/test`;
    const headers = {
        'Content-Type': 'application/json',
        'X-Global-Trace-Id': globalTraceId
    };
    const payload = JSON.stringify({userId});

    const res = http.post(url, payload, {
        headers,
        timeout: "60s"
    });

    // 성공으로 간주하는 HTTP 상태 코드: 200 (OK), 201 (Created), 202(Accepted) 409 (Conflict - 예: 중복 발급 시도)
    check(res, {
        'issue request success': (r) => [200, 201, 202, 409].includes(r.status),
    });

    // 아래 try-catch 블록의 console.log 및 console.error 부분은 메시지 확인용이므로, 성능 테스트 시 해당 부분을 주석 처리합니다.
    /*
    try {
        const result = JSON.parse(res.body);
        console.log(`[${userId}] 발급 상태: ${result.resultType}, 쿠폰 ID: ${result.data?.couponId}`);
    } catch (e) {
        console.error(`[${userId}] 응답 파싱 실패: ${res.status}, k6 오류: ${res.error}, body: ${res.body}`);
    }
    */
}
