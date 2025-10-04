# Coupon System Design

이 프로젝트는 대규모 트래픽 환경에서 발생하는 동시성 이슈를 해결하고,  
메시지 큐를 중심으로 안정적인 쿠폰 발급 아키텍처를 설계하고 구현하며 얻은 기술적인 경험과 고민을 정리했습니다.


> 관련 포스팅

* [MDC와 GlobalTraceId를 활용한 분산 추적](https://devfancy.github.io/SpringBoot-Distributed-Tracing-With-MDC/)

* [Prometheus와 Grafana로 Spring Boot 기반 모니터링 대시보드 구축하기](https://devfancy.github.io/SpringBoot-Monitoring-Prometheus-Grafana/)

---

## Project Overview

본 프로젝트는 블랙 프라이데이, 선착순 이벤트와 같이 대규모 트래픽이 예상되는 상황에서 안정적인 쿠폰 발급을 목표로 설계된 시스템입니다.
동시 요청 속에서도 데이터 정합성을 보장하고, 사용자에게는 빠른 응답 속도를 제공하기 위해 비동기 메시지 큐를 활용한 아키텍처를 구현했습니다.

> 주요 해결 과제

* 대용량 트래픽 제어: Race Condition을 방지하고 정확한 수량 관리를 통한 데이터 무결성 확보

* 응답 시간 최소화: 비동기 처리를 통해 사용자 API 응답 속도 향상

* 안정적인 장애 처리 및 부하 제어: Kafka와 DB로 유입되는 트래픽을 제어하여 시스템 전체의 안정성 확보

---

## Key Achievements & Metrics

가상 유저를 10,000명까지 K6 부하 테스트 도구로 수행한 결과, API 서버에서 다음과 같은 TPS 수치를 기록했습니다.

> Monitoring Results: Coupon API Server

![](/docs/image/coupon-issue-api-v2-tps.png)

* TPS (req/s): Max 4,700

* 5xx Error Rate (%): 0%

---

## System Architecture

### 아키텍처 다이어그램 및 처리 흐름

본 아키텍처는 API 서버와 Consumer 서버의 역할을 명확히 분리하여 병목을 해소하고, 확장성과 안정성을 고려한 구조입니다.

![](/docs/image/coupon-issue-architecture.png)

위 아키텍처의 실제 처리 흐름은 다음과 같은 2단계로 이루어집니다.

> 1단계: API 서버 (선착순 판별 및 Kafka 발행)

* 실시간 선착순 처리 (Redis): 사용자의 요청을 받으면, DB 접근 없이 **Redis**의 원자적 연산을 통해 중복 참여 여부(Set 자료구조)와 선착순 마감 여부(INCR 명령어)를 빠르게 판별합니다.

* 비동기 메시지 발행 (Kafka): 선착순에 성공한 요청은 대기 없이 **Kafka 토픽**으로 즉시 발행됩니다. Producer와 Consumer가 분리되어 API 서버는 사용자에게 빠른 응답을 보장하고, Consumer는 자신의 처리 속도에 맞춰 안정적으로 메시지를 소비합니다.

> 2단계: Consumer 서버 (최종 발급 처리)

* 안정적인 DB 저장: Kafka 컨슈머는 토픽의 메시지를 받아 최종 쿠폰 발급 데이터를 DB에 저장합니다.

* 최종 동시성 제어 (Redisson 분산 락): Kafka의 재처리 등으로 동일 메시지가 중복 소비되는 것을 막기 위해, DB 저장 전 **Redisson 분산 락**을 획득합니다. 이는 불필요한 DB 트랜잭션 비용을 줄이고, DB의 유니크 제약 조건과 함께 데이터 정합성을 이중으로 보장합니다.

### Failure Handling & Scheduled Retry (실패 처리 및 스케줄러 기반 재시도)

* 이전 단계에서 DB 저장 과정에서 장애가 발생하면, 데이터 유실을 방지하는 내결함성 로직이 동작합니다. 먼저 실패한 메시지 정보를 `FailedIssuedCoupon` 테이블에 기록합니다.

* 그 후, Spring `@Scheduled`로 동작하는 스케줄러가 주기적으로 이 테이블의 미처리 건을 조회하여 Kafka 토픽으로 재발행, 발급을 재시도합니다.

* 성공적으로 재처리된 쿠폰은 `isResolved` 상태로 변경하여 중복 발급을 막고 데이터의 최종 정합성을 확보합니다.

---

### Design & Module Structure

> [용어 사전 및 도메인 모델링 관련 문서](https://github.com/devFancy/springboot-coupon-system/blob/main/docs/domain-glossary-and-modeling.md)

프로젝트는 도메인 중심 설계를 기반으로, 각 모듈이 명확한 책임을 갖도록 `멀티 모듈` 구조로 설계되었습니다.

* 도메인 중심 설계: `Coupon`, `IssuedCoupon` 등 핵심 도메인 모델이 비즈니스 로직의 중심이 되도록 구성하여 응집도를 높였습니다.

<img src="/docs/image/coupon-system-design-multi-module.png" width="500">

```markdown
coupon/
├── coupon-api # REST API + 인증 + 비즈니스 로직
├── coupon-consumer # Kafka 비동기 발급 처리
├── coupon-infra # 인프라 모듈 (JPA, Redis, Kafka)
├── coupon-domain # 도메인 모델 (JPA Entity)

support/
├── logging # 공통 로그 필터 및 분산 추적
├── monitoring # Prometheus, Grafana, K6 구성
└── common # 공통 예외 처리, 응답 구조 등
```

### Tech Stack

* Application: Java 17, Spring Boot 3.x

* ORM: Spring Data JPA

* Data Store: MySQL (데이터 영속화), Redis (캐시 및 분산 락)

* Messaging: Kafka (비동기 메시지 큐)

* Monitoring: K6 (부하 테스트), Prometheus (메트릭 수집), Grafana (시각화 대시보드)


---

## API Specification

주요 API 엔드포인트는 다음과 같습니다.

| Method | Endpoint                       | 설명             |
|--------|--------------------------------|----------------|
| POST   | `/api/users/signup`            | 사용자 회원가입       |
| POST   | `/api/auth/login`              | 로그인 후 JWT 발급   |
| POST   | `/api/coupon/`                 | 쿠폰 생성 (관리자)    |
| POST   | `/api/coupon/{couponId}/issue` | 쿠폰 발급 요청 (사용자) |
| POST   | `/api/coupon/{couponId}/usage` | 쿠폰 사용 처리 (사용자) |

### API 응답 예시

* `common` 모듈에서 공통 응답 및 예외 처리를 통일된 구조로 관리하고 있습니다.

* 응답은 `resultType`, `data`, `errorMessage` 세 필드로 구성됩니다.

> 쿠폰 발급 - 성공일 경우

```json
{
  "resultType": "SUCCESS",
  "data": {
    "userId": "a7b4eb1d-391f-4ef9-982e-9b4c0c754d4f",
    "couponId": "6d86751d-a9fa-4c9d-8e93-287b91bfa287",
    "used": false,
    "issuedAt": "2025-05-20T21:41:46.380488"
  },
  "errorMessage": null
}
```

> 쿠폰 발급 - 실패일 경우 (쿠폰이 존재하지 않을 경우)

```json
{
  "resultType": "ERROR",
  "data": null,
  "errorMessage": {
    "code": "E404",
    "message": "존재하지 않는 쿠폰입니다.",
    "data": "존재하지 않는 쿠폰입니다."
  }
}
```

---

## Testing Strategy

대규모 동시성 환경에서의 안정성을 코드 레벨에서 증명하기 위해, 실제 운영과 유사한 시나리오를 기반으로 각 서버의 역할과 책임에 맞는 테스트 전략을 적용했습니다.

### API 서버: 선착순 내에 들어오는지 검증

`API 서버`는 Redis를 통해 빠르고 효율적으로 선착순 요청을 처리하는 관문 역할을 합니다.
따라서 테스트는 Redis 기반의 제어 로직이 많은 동시 요청 하에서 정확하게 동작하는지를 집중적으로 검증합니다.

> 관련 클래스명: [CouponServiceImplTest](https://github.com/devFancy/springboot-coupon-system/blob/main/coupon/coupon-api/src/test/java/dev/be/coupon/api/coupon/application/CouponServiceImplTest.java)

* 중복 요청 방지 테스트(`fail_issue_request_due_to_duplicate_entry`)

    * 시나리오: 한 명의 사용자가 동일한 쿠폰 발급을 연속으로 두 번 요청합니다.

    * 검증: 첫 번째 요청은 `SUCCESS`로 접수되지만, 두 번째 요청은 Redis의 Set 자료구조에 의해 중복으로 감지되어 `DUPLICATE`를 반환하는지 확인합니다.

* 선착순 마감 테스트(`fail_issue_request_due_to_sold_out`)

    * 시나리오: 발급 수량이 1개인 쿠폰에 대해 두 명의 사용자가 순차적으로 발급을 요청합니다.

    * 검증: 첫 번째 사용자는 `SUCCESS`를 반환받고, Redis의 INCR 카운터가 소진되어 두 번째 사용자는 `SOLD_OUT`을 반환받는지 확인합니다.

* 동시 요청에 대한 테스트

    * 시나리오: 100개 한정 수량의 쿠폰에 대해 10,000건의 동시 발급 요청을 시뮬레이션합니다.

    * 검증: Race Condition 없이 정확히 100개의 요청만 `SUCCESS` 응답을 받고, 나머지 9,900개의 요청은 `SOLD_OUT` 처리되는지 검증합니다. 이 테스트는 API 서버의 선착순
      처리 성능과 정확성을 보장합니다.

### Consumer 서버: DB에 안정적으로 저장되는지 검증

`Consumer 서버`는 Kafka로부터 전달받은 메시지를 최종적으로 DB에 저장하는 역할을 합니다. 따라서 테스트는 메시지를 유실하거나 중복 저장하지 않고, 안정적으로 처리하는지를 집중적으로 검증합니다.

> 관련 클래스명: [CouponIssuanceServiceImplTest](https://github.com/devFancy/springboot-coupon-system/blob/main/coupon/coupon-consumer/src/test/java/dev/be/coupon/kafka/consumer/application/CouponIssuanceServiceImplTest.java)

* API 서버로부터 요청받은 메시지 처리 테스트

    * 시나리오: API 서버가 선별한 100개의 유효한 발급 메시지를 테스트용 Kafka 토픽으로 발행합니다.

    * 검증:  100개의 메시지가 모두 누락 없이 처리되어 DB에 정확히 100개의 발급된 쿠폰 테이블의 데이터에 저장되는지 확인합니다.

---

## How to Run

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

[4] 모니터링 대시보드 확인 (Optional)

애플리케이션이 정상적으로 실행되면 아래 주소에서 모니터링 대시보드를 확인할 수 있습니다.

* Grafana: `http://localhost:3000` (ID/PW: admin/admin)

* Prometheus: `http://localhost:9090`

---

## Reference

* [[올리브영 테크블로그] 올리브영 초대량 쿠폰 발급 시스템 개선기](https://oliveyoung.tech/2024-12-11/oliveyoung-coupon-mess-issue/?keyword=쿠폰)

* [[인프런] 실습으로 배우는 선착순 이벤트 시스템](https://www.inflearn.com/course/선착순-이벤트-시스템-실습)

* [MAU 600만 서비스의 쿠폰함 성능 개선기: 백엔드 개발자의 고군분투](https://cwbeany.com/story/19)
