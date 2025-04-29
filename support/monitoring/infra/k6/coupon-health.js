import http from 'k6/http';
import {sleep} from 'k6';

export const options = {
    vus: 10,
    summaryTimeUnit: 's',
    duration: '600s',
};

export default function () {
    const url = 'http://localhost:8080/api/health';

    http.get(url, {
        tags: {name: 'HealthCheck'},
        timeout: '30s',
        noConnectionReuse: true,
    });
    sleep(1);
}
