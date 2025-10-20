# Spring Boot 기반 쿠폰 발급 시스템의 부하 테스트 및 모니터링 구성 가이드

> 자세한 내용은 [3편. Prometheus와 Grafana로 Spring Boot 기반 모니터링 대시보드 구축하기](https://devfancy.github.io/SpringBoot-Monitoring-Prometheus-Grafana/)을 참고해 주시기 바랍니다.

## 전체 시각화

```
[K6]
 ├─> [Coupon API (Spring Boot)]
 │     ├─> [Redis] (중복 발급 방지, 선착순 마감 여부 판별)
 │     ├─> [Kafka] (비동기 발급 처리(Producer) - 메시지 전달)
 ├─> [Coupon Consumer (Spring Boot)]
 │     ├─> [Kafka] (비동기 발급 처리(Consumer) - 메시지 수신)
 │     └─> [DB] (쿠폰 데이터 저장)
 └─> [InfluxDB] (K6 테스트 결과 저장)

[Coupon API, Coupon Consumer]
 └─> /actuator/prometheus ─> [Prometheus] ─> [Grafana]
```

## 시스템 구성 및 흐름 설명

[1] 사용자는 K6 부하 테스트 스크립트를 실행한다.

- 각 요청은 HTTP POST 방식으로 Coupon API의 쿠폰 발급 엔드포인트로 전송된다.

[2] Coupon API는 아래 작업을 수행한다.

- Redis를 통해 중복 발급 여부를 검사한다.
- Kafka를 통해 쿠폰 발급 요청을 비동기 처리 큐로 전송한다.
- 실제 쿠폰 발급 결과는 DB(JPA 기반)로 저장된다.

[3] K6는 요청/응답 정보를 InfluxDB에 저장한다.

- InfluxDB는 K6에서 수집한 성능 메트릭(요청 수, 응답 시간 등)을 저장하며,
  Grafana는 이를 기반으로 부하 테스트 결과를 실시간 시각화한다.

[4] Coupon API는 `/actuator/prometheus` 엔드포인트를 통해 시스템 메트릭을 노출한다.

- Prometheus는 이 엔드포인트를 주기적으로 scrape(수집) 하여 데이터를 저장한다.

[5] Prometheus에서 수집한 메트릭은 Grafana 대시보드로 시각화된다.

- 대표적으로 TPS, 평균 응답 시간, 에러율 등을 실시간으로 확인할 수 있다.

---

> Grafana - influxdb Dashboards 만들기

- Dashboard - 우측 New - import 문 클릭

![](/docs/image/influxdb-dashboard-1.png)

- influxdb 1.x 버전은 대시보드 ID가 `2587` 이므로 해당 부분을 입력하고 `Load` 버튼을 클릭한다.
- 그리고 k6 부분에 해당 influxdb를 선택하고 `Import`  버튼을 누른다.

![](/docs/image/influxdb-dashboard-2.png)

- 사전 조건:
    - Consumer 애플리케이션 -> API 애플리케이션 순으로 실행한 뒤 아래 명령어를 순서대로 입력한다.
    - k6 스크립트 파일이 위치한 경로로 이동한다. (cd springboot-coupon-system/support/monitoring/infra/k6)

- 쿠폰 생성 - 명령어: `k6 run --out influxdb=http://localhost:8086/k6 coupon-create-test.js`
- 쿠폰 발급 - 명령어: `k6 run --out influxdb=http://localhost:8086/k6 coupon-issue-test.js`
    - 해당 명령어를 입력하면 아래와 같이 dashboard 에 반영된 것을 확인할 수 있다.

![](/docs/image/influxdb-dashboard-3.png)
