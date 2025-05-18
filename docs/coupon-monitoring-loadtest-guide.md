# Spring Boot 기반 쿠폰 발급 시스템의 부하 테스트 및 모니터링 구성 가이드

## 전체 시각화

```
K6 -> InfluxDB
K6 -> Coupon API
Coupon API -> Redis + Kafka + DB
Coupon API -> /actuator/prometheus → Prometheus -> Grafana
```

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

- InfluxDB 기반 대시보드 (K6 연동) 
  - 위치: Home > Dashboards > K6 Load Test for Coupon Issue
  - 설정 위치
    - DataSource: `support/monitoring/infra/grafana/datasources/datasource.yml`
    - Dashboard: `support/monitoring/infra/grafana/dashboards/dashboard.yml`
  - 주의
    - InfluxDB `max-values-per-tag` 초과 방지를 위해 쿼리 파라미터 대신 RequestBody 방식 사용 권장

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
- POST 요청은 RequestBody(JSON) 기반으로 userId 전송

## 튜닝 포인트 및 주의사항

- 병목 발생 시 조정 가능한 항목
  - spring.tomcat.threads.max
  - spring.kafka.listener.concurrency
  - spring.datasource.hikari.maximum-pool-size

- Redis 락/큐 로직 병목 여부 모니터링 권장
- 운영 트래픽과 모니터링 트래픽을 포트(8080 vs 8082)로 분리
