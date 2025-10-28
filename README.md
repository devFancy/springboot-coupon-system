# Coupon System Design

## 1. Project Overview

본 프로젝트는 선착순 이벤트와 같이 대규모 동시 요청이 집중되는 상황에서 안정적인 쿠폰 발급을 목표로 설계된 시스템입니다.

RDBMS의 커넥션 풀 한계와 락(Lock) 경합으로 인한 병목 현상 없이, 수천 TPS(Transactions Per Second) 수준의 트래픽을 처리하기 위해 Redis의 원자적 연산과 Kafka 메시지 큐를 활용한 비동기 아키텍처를 구현했습니다.

### 주요 해결 과제

* 트래픽 처리: 10,000명 이상의 동시 사용자를 가정하고, DB에 직접적인 부하를 주지 않으면서 초당 4,000건 이상의 요청을 처리합니다.

* 데이터 정합성: Race Condition을 방지하고 정확한 쿠폰 수량 관리를 통해 데이터 무결성을 확보합니다. (선착순/중복 발급 제어)

* 낮은 응답 시간 (Latency): 비동기 처리를 통해 사용자 API의 응답 속도를 50ms 미만으로 유지합니다.

* 안정적인 장애 처리: 특정 컴포넌트(DB, Consumer)의 장애가 전체 시스템의 장애로 전파되지 않도록 격리하고, 데이터 유실 없는 재처리(Retry) 로직을 구현합니다.


---

## 2. Key Achievements & Metrics

부하 테스트 도구(K6)를 사용하여 가상 유저 10,000명 시나리오로 테스트를 수행했습니다.

API 서버는 DB 병목 현상 없이 최대 4,700 TPS를 기록했으며, 전 구간에서 5xx 에러율 0%를 달성했습니다.

> Monitoring Results: Coupon API Server

![](/docs/image/coupon-issue-api-v2-tps.png)

* TPS (req/s): Max 4,700 (`coupon-api` 기준)

* 5xx Error Rate (%): 0%


---

## 3. System Architecture

![](/docs/image/Coupon-Issue-System-Architecture.png)

본 아키텍처는 API 서버(요청 접수)와 Consumer 서버(발급 처리)의 역할을 명확히 분리하여 병목을 해소하고, 확장성과 안정성을 고려한 구조입니다.

### 쿠폰 발급 2-Stage 처리 흐름

쿠폰 발급에 대한 처리 흐름은 다음과 같은 두 단계로 이루어집니다.

> Stage 1: API 서버 (선착순 판별 및 Kafka 발행)

* 중복 참여 및 선착순 검증 (Redis): DB 접근 시 발생하는 락 경합(Lock Contention)을 피하기 위해, In-Memory 데이터 저장소인 Redis를 '문지기' 역할로 활용합니다.

  * SADD: 중복 참여자인지 확인 (Set 자료구조, O(1))

  * INCR: 선착순 수량을 카운트 (Atomic 연산, O(1))

* 비동기 발행 (Kafka): Redis 검증을 통과한 요청은 즉시 Kafka 토픽에 메시지를 발행됩니다. API 서버는 DB 트랜잭션을 기다리지 않고 사용자에게 `요청 성공` 응답을 즉시 반환하여, DB 부하와 관계없이 높은 TPS를 유지합니다.

> Stage 2: Consumer 서버 (최종 발급 처리 및 정합성 보장)

* 안정적인 DB 저장: Kafka 컨슈머는 자신의 처리 속도(처리량)에 맞춰 메시지를 폴링(poll)하여 최종 쿠폰 발급 데이터를 DB에 저장합니다.

* 최종 동시성 제어 (Redisson 분산 락): Kafka의 `Exactly-Once` 보장 설정에도 불구하고, 재처리 로직 등으로 인해 동일 메시지가 중복 소비될 가능성에 대비합니다. DB 저장 전 Redisson 분산 락을 획득하여 불필요한 DB 트랜잭션 비용을 줄이고, DB의 유니크 제약 조건과 함께 데이터 정합성을 이중으로 보장합니다.

### 설계 구조

* Layered Architecture: Controller, Service, Repository 계층을 명확히 분리하는 계층형 아키텍처를 기반으로 설계되었습니다. 

> 용어사전 및 도메인 모델링에 대한 자세한 내용은 [쿠폰 도메인 모델링 문서](https://github.com/devFancy/springboot-coupon-system/blob/main/docs/coupon-domain-modeling.md)에서 확인하실 수 있습니다.

* Multi-Module: 도메인(coupon-domain), 인프라(coupon-infra), 애플리케이션(coupon-api, coupon-consumer) 등 각 모듈이 명확한 책임을 갖도록 멀티 모듈 구조로 프로젝트를 구성하여 응집도를 높였습니다.

```markdown
coupon/
├── coupon-api # (1) 사용자 요청 접수
├── coupon-consumer # (2) 비동기 발급 처리
├── coupon-infra # 인프라 모듈 (JPA, Redis, Kafka)
├── coupon-domain # 도메인 모델 (Entity, Repository Interfaces)

support/
├── logging # 공통 로그 필터 및 분산 추적
├── monitoring # Prometheus, Grafana, K6, Promtail, Loki 구성
```


---

## 4. Core Technical Decisions

이 시스템을 설계하며 가장 중요하게 고려했던 기술적 결정과 그 이유를 설명합니다.

> 왜 Kafka와 Redis를 사용했는가?

* 문제점: 10,000명의 동시 요청이 발생하면, 100개 내외의 커넥션 풀을 가진 RDBMS는 즉시 병목 상태가 됩니다.
  단순한 쿼리도 수천 개의 동시 트랜잭션이 몰리면 락 경합으로 인해 시스템 전체가 마비됩니다.

* 해결(Redis): `INCR` (Atomic Counter)와 `SADD` (Set) 같은 Redis의 원자적 연산은 초당 수만 건의 요청을 락 없이(single-threaded) 처리할 수 있습니다.
  이를 통해 DB 앞단에서 1차적으로 선착순과 중복을 빠르게 판별할 수 있었습니다.

* 해결 (Kafka): Redis를 통과한 '유효한' 요청조차도 수천 건에 달할 수 있습니다.
  Kafka를 버퍼(Buffer) 기반 큐로 사용하여 API 서버의 빠른 응답과 Consumer의 느린 DB 저장 속도 차이를 분리했습니다.
  이로 인해 DB 장애가 발생하더라도 사용자 요청은 정상적으로 접수(ack)될 수 있습니다.

---

## 5. Observability

분산 시스템 환경에서는 요청이 여러 컴포넌트(API, Kafka, Consumer)에 걸쳐 있어 장애 추적이 어렵습니다.

본 프로젝트는 '관측 가능성(Observability)'의 3요소(Metric, Trace, Log)를 구축하여 이 문제를 해결했습니다.

### Metrics: Prometheus & Grafana

* Prometheus: `coupon-api`, `coupon-consumer` 서버의 JVM, Spring Boot Actuator, Kafka Consumer Lag 등 핵심 지표(Metric)를 주기적으로 수집합니다.

* Grafana: K6 부하 테스트의 실시간 TPS, Error Rate, Latency 및 Consumer Lag을 시각화하여 시스템의 병목 지점을 직관적으로 파악합니다.

### Distributed Tracing: Micrometer Tracing & Sentry

* TraceId: 사용자의 최초 요청(`coupon-api`)부터 Kafka를 거쳐 `coupon-consumer` 가 DB에 저장하기까지의 모든 과정을 `globalTraceId` 하나로 묶어 추적합니다.

* Sentry (Error Tracking): Grafana가 감지하지 못하는 코드 레벨의 예외를 실시간으로 수집합니다.
  에러 발생 시 Sentry는 `globalTraceId`와 `errorCode`를 태그로 자동 수집하여, 어떤 요청(TraceId)이 어떤 에러(Sentry)를 발생시켰는지 즉시 특정할 수 있습니다.

### Centralized Logging: Grafana Loki & Promtail

* Loki: Sentry에서 확인한 `globalTraceId` 를 복사하여 Grafana Loki 대시보드에서 검색하면, 해당 요청과 관련된 모든 정상 로그까지 중앙에서 조회할 수 있습니다.

* 성과: 이 3가지 요소를 연동함으로써, 장애 발생 시 '메트릭(Grafana) -> 에러(Sentry) -> 상세 로그(Loki)'로 이어지는 장애 추적 경로를 확보하여, 신속한 원인 분석이 가능합니다.


---

## 6. Tech Stack

| **Category**    | **Tech**                         | **Description**                |
|-----------------|----------------------------------|--------------------------------|
| Backend         | Java 17, Spring Boot 3.x         |                                |
| ORM             | Spring Data JPA                  |                                |
| Data Store      | MySQL 8.x                        | 쿠폰 발급 내역 등 데이터 영속화             |
| In-Memory Store | Redis                            | 선착순/중복 발급 제어                   |
| Messaging       | Kafka                            | 비동기 요청 처리를 위한 메시지 큐            |
| Concurrency     | Redisson                         | Consumer 단의 최종 정합성 보장을 위한 분산 락 |
| Observability   | [Metrics] Prometheus, Grafana    | 실시간 성능 지표 모니터링 및 대시보드          |
|                 | [Tracing] Micrometer Tracing     | MSA 환경 분산 추적                   |
|                 | [Error] Sentry                   | 애플리케이션 에러 및 예외 수집/알림           |
|                 | [Logging] Grafana Loki, Promtail | 중앙화된 로그 수집 및 검색                |
| DB Migration    | Flyway                           | SQL 기반의 안정적인 DB 스키마 버전 관리      |
| API Docs        | Swagger (Springdoc OpenAPI)      | API 명세 자동화 및 테스트 UI            |
| Testing         | K6, JUnit 5, Mockito             | 부하 테스트 및 단위/통합 테스트             |
| DevOps          | Docker Compose                   | 로컬 개발 환경 구성                    |


---

## 7. API Specification

애플리케이션 실행 후, `http://localhost:8080/service-docs.html` 에서 전체 API 명세 및 테스트를 직접 수행할 수 있습니다.

| Method | Endpoint                       | 설명             |
|--------|--------------------------------|----------------|
| POST   | `/api/users/signup`            | 사용자 회원가입       |
| POST   | `/api/auth/login`              | 로그인 후 JWT 발급   |
| POST   | `/api/coupon/`                 | 쿠폰 생성 (관리자)    |
| POST   | `/api/coupon/{couponId}/issue` | 쿠폰 발급 요청 (사용자) |
| POST   | `/api/coupon/{couponId}/usage` | 쿠폰 사용 처리 (사용자) |


---

## 8. Testing Strategy

높은 동시성 환경에서의 안정성을 코드 레벨에서 증명하기 위해, 실제 운영과 유사한 시나리오를 기반으로 각 서버의 역할과 책임에 맞는 테스트 전략을 적용했습니다.

### API 서버: 선착순 내에 들어오는지 검증

`API 서버`는 Redis를 통해 빠르고 효율적으로 선착순 요청을 처리하는 관문 역할을 합니다.
따라서 테스트는 Redis 기반의 제어 로직이 많은 동시 요청 하에서 정확하게 동작하는지를 집중적으로 검증합니다.

> 중복 요청 방지 테스트

* 시나리오: 한 명의 사용자가 동일한 쿠폰 발급을 연속으로 두 번 요청합니다.

* 검증: 첫 번째 요청은 `SUCCESS`로 접수되지만, 두 번째 요청은 Redis의 Set 자료구조에 의해 중복으로 감지되어 `DUPLICATE`를 반환하는지 확인합니다.

> 선착순 마감 테스트

* 시나리오: 발급 수량이 1개인 쿠폰에 대해 두 명의 사용자가 순차적으로 발급을 요청합니다.

* 검증: 첫 번째 사용자는 `SUCCESS`를 반환받고, Redis의 INCR 카운터가 소진되어 두 번째 사용자는 `SOLD_OUT`을 반환받는지 확인합니다.

> 동시 요청에 대한 테스트

* 시나리오: 100개 한정 수량의 쿠폰에 대해 10,000건의 동시 발급 요청을 시뮬레이션합니다.

* 검증: Race Condition 없이 정확히 100개의 요청만 `SUCCESS` 응답을 받고, 나머지 9,900개의 요청은 `SOLD_OUT` 처리되는지 검증합니다. 이 테스트는 API 서버의 선착순
  처리 성능과 정확성을 보장합니다.

### Consumer 서버: DB에 안정적으로 저장되는지 검증

`Consumer 서버`는 Kafka로부터 전달받은 메시지를 최종적으로 DB에 저장하는 역할을 합니다. 따라서 테스트는 메시지를 유실하거나 중복 저장하지 않고, 안정적으로 처리하는지를 집중적으로 검증합니다.

> 동시 요청 처리 테스트

* 시나리오: ExecutorService와 CountDownLatch를 사용하여 100개의 스레드가 동시에 쿠폰 발급 메시지를 Kafka 토픽으로 발행하는 상황을 시뮬레이션합니다.

* 검증: 100개의 메시지가 모두 누락 없이 처리되어 DB에 정확히 100개의 발급된 쿠폰 테이블의 데이터에 저장되는지 확인합니다.


---

## 9. How to Run

[1] 프로젝트 빌드 (Build Project)

가장 먼저 프로젝트를 빌드해야 합니다. 프로젝트 최상단 디렉토리에서 아래 명령어를 실행하세요.

* 명령어: ./gradlew clean build

[2] 인프라 실행 (Start Infrastructure)

docker-compose를 사용하여 Redis, Kafka, MySQL 등 외부 인프라를 실행합니다.

* 명령어: docker compose up -d (프로젝트 최상단 위치에서 실행)

[3] 애플리케이션 실행 (Run Applications)

IntelliJ IDE에서 아래 두 개의 Spring Boot 애플리케이션을 각각 실행합니다.

* `coupon-api` 모듈의 CouponApiApplication.java

* `coupon-consumer` 모듈의 CouponKafkaConsumerApplication.java

**Note**: `coupon-api` 애플리케이션이 처음 실행될 때, Flyway가 `resources/db/migration` 경로의 SQL 파일을 읽어 자동으로 DB 스키마를 최신 상태로 마이그레이션합니다.

[4] 모니터링 대시보드 확인 (Optional)

애플리케이션이 정상적으로 실행되면 아래 주소에서 모니터링 대시보드를 확인할 수 있습니다.

* Swagger UI (API Docs): http://localhost:8080/service-docs.html

* Grafana: `http://localhost:3000` (ID/PW: admin/admin)

* Prometheus: `http://localhost:9090`
