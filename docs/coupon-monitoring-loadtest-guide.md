# Spring Boot 기반 쿠폰 발급 시스템의 부하 테스트 및 모니터링 구성 가이드

## 전체 시각화

```
[K6]
 ├─> [Coupon API (Spring Boot)]
 │     ├─> [Redis] (중복 발급 방지)
 │     ├─> [Kafka] (비동기 발급 처리)
 │     └─> [DB] (쿠폰 데이터 저장)
 └─> [InfluxDB] (K6 테스트 결과 저장)

[Coupon API]
 └─> /actuator/prometheus ─> [Prometheus] ─> [Grafana]
```

### 시스템 구성 및 흐름 설명

[1]. 사용자는 K6 부하 테스트 스크립트를 실행한다.
  - 각 요청은 HTTP POST 방식으로 Coupon API의 쿠폰 발급 엔드포인트로 전송된다.

[2]. Coupon API는 아래 작업을 수행한다.
  - Redis를 통해 중복 발급 여부를 검사한다.
  - Kafka를 통해 쿠폰 발급 요청을 비동기 처리 큐로 전송한다.
  - 실제 쿠폰 발급 결과는 DB(JPA 기반)로 저장된다.

[3]. K6는 요청/응답 정보를 InfluxDB에 저장한다.
  - InfluxDB는 K6에서 수집한 성능 메트릭(요청 수, 응답 시간 등)을 저장하며,
    Grafana는 이를 기반으로 부하 테스트 결과를 실시간 시각화한다.

[4]. Coupon API는 `/actuator/prometheus` 엔드포인트를 통해 시스템 메트릭을 노출한다.
  - Prometheus는 이 엔드포인트를 주기적으로 scrape(수집) 하여 데이터를 저장한다.

[5]. Prometheus에서 수집한 메트릭은 Grafana 대시보드로 시각화된다.
  - 대표적으로 TPS, 평균 응답 시간, 에러율 등을 실시간으로 확인할 수 있다.

---

## Load Test

쿠폰 발급 부하 테스트
- 테스트 대상 URL: `http://localhost:8080/api/coupon/{couponId}/issue/test`

Docker 기반 구성 요소 실행

```bash
cd ./springboot-coupon-system
docker compose up -d # Redis, Kafka, Prometheus, Grafana, InfluxDB 등 포함
```

## Prometheus

- 접속 Url: `http://localhost:9090`
- Scrape 대상: `host.docker.internal:8082/actuator/prometheus`
- 설정 위치: `./support/monitoring/infra/prometheus/prometheus.yml`
- 참고: Spring Actuator + Micrometer 기반 메트릭 수집

## Grafana

- Url: http://localhost:3000
- Login
  - ID: admin
  - PW: admin

### Dashboards

- Prometheus
  - 위치: `Home > Dashboards > TPS, Response Time for Coupon Issue`
  - TPS (초당 요청 수, req/s)
    - rate(http_server_requests_seconds_count{uri="/api/coupon/{couponId}/issue/test"}[1m])
  - 평균 응답 시간 (ms)
    - rate(http_server_requests_seconds_sum{uri="/api/coupon/{couponId}/issue/test"}[1m]) 
      / rate(http_server_requests_seconds_count{uri="/api/coupon/{couponId}/issue/test"}[1m]) * 1000
  - 에러율 (5xx 비율, %)
    - 100 * rate(http_server_requests_seconds_count{uri="/api/coupon/{couponId}/issue/test",status=~"5.."}[1m])
      / rate(http_server_requests_seconds_count{uri="/api/coupon/{couponId}/issue/test"}[1m])
    - 의미: 전체 요청 대비 5xx 응답 비율

- InfluxDB 기반 대시보드 (K6 연동) 
  - 위치: `Home > Dashboards > K6 Load Test for Coupon Issue`
  - 설정 위치
    - DataSource: `support/monitoring/infra/grafana/datasources/datasource.yml`
    - Dashboard: `support/monitoring/infra/grafana/dashboards/dashboard.yml`
  - 주의
    - InfluxDB `max-values-per-tag` 초과 방지를 위해 쿼리 파라미터 대신 RequestBody 방식 사용 권장

> Grafana - influxdb Dashboards 만들기

- Dashboard - 우측 New - import 문 클릭

![](/docs/image/influxdb-dashboard-1.png)

- influxdb 1.x 버전은 대시보드 ID가 `2587` 이므로 해당 부분을 입력하고 `Load` 버튼을 클릭한다.
- 그리고 k6 부분에 해당 influxdb를 선택하고 `Import`  버튼을 누른다.

![](/docs/image/influxdb-dashboard-2.png)

- 명령어: k6 run --out influxdb=http://localhost:8086/k6 coupon-issue-test.js
  - 해당 명령어를 입력하면 아래와 같이 dashboard 에 잘 반영된 것을 확인할 수 있다.

![](/docs/image/influxdb-dashboard-3.png)

## Spring Boot Monitoring 설정

- metrics endpoint
  - Url: http://localhost:8082/actuator/prometheus

- application.yml에서 import

```yml
spring:
  config:
    import:
      - monitoring.yml
```

- monitoring.yml

```yml
management:
  server:
    port: 8082
  endpoints:
    web:
      exposure:
        include: prometheus
```

## K6

- 위치: cd ./support/monitoring/infra/k6

> 쿠폰 발급을 하기 위해서는 먼저 쿠폰을 생성해야 합니다.

실행 명령어
```bash
# 쿠폰 생성
k6 run --out influxdb=http://localhost:8086/k6 coupon-create-test.js

# 쿠폰 발급
k6 run --out influxdb=http://localhost:8086/k6 coupon-issue-test.js
```

- `uuidv4()` 를 통해 사용자 ID 생성
- POST 요청은 RequestBody(JSON) 기반으로 `userId`를 전송합니다.
  - 이유: InfluxDB의 태그 수 제한(max-values-per-tag)을 방지하기 위해 URL 파라미터가 아닌 Body 전송 사용

## 튜닝 포인트 및 주의사항

- 병목 발생 시 조정 가능한 항목
  - spring.tomcat.threads.max
  - spring.kafka.listener.concurrency
  - spring.datasource.hikari.maximum-pool-size

- Redis 락/큐 로직 병목 여부 모니터링 권장
- 운영 트래픽과 모니터링 트래픽을 포트(8080 vs 8082)로 분리
