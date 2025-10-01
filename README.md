# Coupon System Design

이 프로젝트는 대규모 트래픽 환경에서 발생하는 동시성 이슈를 해결하고,  
**비동기 메시지 큐**(Message Queue)를 중심으로 안정적인 쿠폰 발급 아키텍처를 설계하고 구현하며 얻은 기술적인 경험과 고민을 정리했습니다.


> 관련 포스팅

* [MDC와 GlobalTraceId를 활용한 분산 추적](https://devfancy.github.io/SpringBoot-Distributed-Tracing-With-MDC/)

* [Prometheus와 Grafana로 Spring Boot 기반 모니터링 대시보드 구축하기](https://devfancy.github.io/SpringBoot-Monitoring-Prometheus-Grafana/)


---

## Project Overview

본 프로젝트는 블랙 프라이데이, 선착순 이벤트와 같이 
초당 수천 건 이상의 대규모 트래픽이 예상되는 상황에서 안정적인 쿠폰 발급을 목표로 설계된 시스템입니다.
초당 수천 건의 동시 요청 속에서도 데이터 정합성을 보장하고, 사용자에게는 빠른 응답 속도를 제공하기 위해 비동기 메시지 큐를 활용한 아키텍처를 구현했습니다.

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

* 빠른 선착순 처리: 사용자의 요청을 받으면, Redis의 원자적 연산(`ZADD NX`, `INCR`)을 통해 중복 참여 여부와 선착순 마감 여부를 실시간으로 판별합니다. 
    이 과정은 DB 접근 없이 메모리에서만 동작하여 매우 빠릅니다.

* 즉시 발행: 선착순에 성공한 요청은 대기 없이 **즉시 Kafka 토픽**으로 발행됩니다. 그 후 API 서버는 사용자에게 성공 응답을 반환하고 역할을 마칩니다.

> 2단계: Consumer 서버 (최종 발급 처리)

* 비동기 처리: Kafka 컨슈머는 토픽에 발행된 메시지를 받아 최종 쿠폰 발급 데이터를 DB에 저장합니다. 
    이 과정은 API 요청 흐름과 완전히 분리되어 동작합니다.

* DB 저장 시에는 Redisson 분산 락을 사용하여, 혹시 모를 중복 실행이 발생하더라도 데이터 정합성을 최종적으로 보장합니다.


### Failure Handling & Scheduled Retry (실패 처리 및 스케줄러 기반 재시도)

* 이전 단계에서 DB 저장 과정에서 장애가 발생하면, 데이터 유실을 방지하는 내결함성 로직이 동작합니다. 먼저 실패한 메시지 정보를 `FailedIssuedCoupon` 테이블에 기록합니다.

* 그 후, Spring `@Scheduled`로 동작하는 스케줄러가 주기적으로 이 테이블의 미처리 건을 조회하여 Kafka 토픽으로 재발행, 발급을 재시도합니다.

* 성공적으로 재처리된 쿠폰은 `isResolved` 상태로 변경하여 중복 발급을 막고 데이터의 최종 정합성을 확보합니다.


---

### Design & Module Structure

> 도메인 모델링 및 용어 정의는 해당 [문서](https://github.com/devFancy/springboot-coupon-system/blob/main/docs/domain-glossary-and-modeling.md)에 상세히 정리했습니다.

프로젝트는 도메인 중심 설계를 기반으로, 각 모듈이 명확한 책임을 갖도록 **멀티 모듈** 구조로 설계되었습니다.

* 도메인 중심 설계: `Coupon`, `IssuedCoupon` 등 핵심 도메인 모델이 비즈니스 로직의 중심이 되도록 구성하여 응집도를 높였습니다. (e.g., 쿠폰 수량 관리 로직은 `Coupon` 엔티티 내부에서 제어)

* 멀티 모듈 구조: 각 모듈의 역할과 의존성을 명확히 분리하여 유연하고 확장 가능한 구조를 구현했습니다.

<img src="/docs/image/coupon-system-design-multi-module.png" width="500">

```markdown
coupon/
├── coupon-api              # REST API + 인증 + 비즈니스 로직
├── coupon-consumer         # Kafka 비동기 발급 처리
├── coupon-infra            # 인프라 모듈 (JPA, Redis, Kafka)
├── coupon-domain           # 도메인 모델 (JPA Entity)

support/
├── logging                 # 공통 로그 필터 및 설정
├── monitoring              # Prometheus, Grafana, K6 구성
└── common                  # 공통 예외 처리, 응답 구조 등
```

### Tech Stack

본 프로젝트는 안정성과 확장성을 고려하여 다음 기술들을 도입했으며, 각 기술의 선정 이유는 다음과 같습니다.

* Java 17 & Spring Boot 3.x: 안정적인 LTS 버전과 빠른 개발 속도, 강력한 멀티스레드 지원을 위해 선택했습니다.

* Redis: 실시간 선착순 제어 및 분산 락 구현을 위해 도입했습니다.
    * 중복 참여 방지: Set 자료구조의 SADD, SREM를 사용하여 사용자의 중복 참여 요청을 검증합니다.
    * 선착순 보장: INCR의 원자적 연산을 통해 동시성 이슈 없이 모든 요청에 고유한 순번을 부여하고, 선착순 마감 여부를 판단합니다.
    * 최종 처리의 동시성 제어: 발급을 처리하는 컨슈머 단계에서는 `Redisson` 분산 락을 활용했습니다. 이를 통해 DB에 접근하기 전, 락을 먼저 획득하여 중복 실행 자체를 사전에 방지합니다. 이를 통해 불필요한 DB 트랜잭션 시도와 롤백 비용을 줄여줍니다. 이는 DB의 유니크 제약과 함께 성능까지 고려했습니다. 

* Kafka: 대량의 쿠폰 발급 요청을 비동기적으로 처리하여 API 응답 시간을 최소화하고, 시스템 전체의 처리량을 향상시키기 위해 도입했습니다.
    * 안정적인 처리량 제어: Producer(API 서버)와 Consumer를 완전히 분리하여, API 서버는 요청을 즉시 발행하고 Consumer는 자신의 처리 속도에 맞게 메시지를 소비함으로써 시스템 전체의 안정성을 확보했습니다.
    * 데이터 유실 방지: 모든 발급 요청을 디스크 기반의 로그(Log)에 기록하여, 컨슈머 장애 시에도 데이터 유실 없이 안정적인 재처리를 보장합니다. 이는 `Failure Handling` 로직의 기반이 됩니다.

* MySQL: 데이터의 정합성이 중요한 쿠폰 발급 정보, 사용자 데이터 등을 안정적으로 저장하기 위해 관계형 데이터베이스를 사용했습니다.

* JPA: 객체지향적인 도메인 모델링과 동적 쿼리 생성을 통해 생산성과 유지보수성을 높였습니다.

* K6, Prometheus & Grafana: 부하 테스트 및 모니터링 환경을 구축하여 아키텍처 개선의 효과를 정량적으로 검증하고, 시스템의 병목 지점을 시각적으로 분석하여 성능을 최적화했습니다.


---

## API Specification

주요 API 엔드포인트는 다음과 같습니다.

| Method | Endpoint                       | 설명             |
|--------|--------------------------------|----------------|
| POST   | `/api/users/signup`            | 사용자 회원가입       |
| POST   | `/api/auth/login`              | 로그인 후 JWT 발급   |
| POST   | `/api/coupon/`                 | 쿠폰 생성 (관리자 전용) |
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

대규모 동시성 환경에서의 안정성을 코드 레벨에서 증명하기 위해, 실제 운영과 유사한 시나리오를 기반으로 다음과 같은 테스트 전략을 적용했습니다


### Unit/Integration Test

주요 도메인과 비즈니스 로직의 정확성을 보장하기 위해 단위/통합 테스트를 작성했으며, 특히 아래 항목들을 집중적으로 검증합니다.

* 중복 발급 및 수량 제한: 한정된 수량의 쿠폰이 정확히 제한된 개수만큼만 발급되는지 확인합니다.

* End-to-End 데이터 흐름: Kafka 메시지 발행(Produce)부터 소비(Consume)를 거쳐 DB에 최종 저장되기까지의 전체 파이프라인이 정상 동작하는지 검증합니다.

* 데이터 정합성: 쿠폰 발급 시 Redis와 RDB 간의 데이터 상태가 일관되게 유지되는지 확인합니다.

* 예외 처리: 분산 락 획득 실패, 재고 소진, DB 연결 실패 등 다양한 예외 상황에서 시스템이 의도대로 동작하는지 테스트합니다.


### Multi-Thread Concurrency test

이 프로젝트의 핵심인 동시성 제어 로직을 검증하기 위해, ExecutorService와 CountDownLatch를 활용하여 실제와 유사한 병렬 요청 환경을 시뮬레이션했습니다.

* 시나리오 1 (중복 발급 방어):

  * 가정: 한 명의 사용자가 동시에 수백 번의 발급 요청을 보냅니다.
  
  * 검증: 오직 단 1개의 쿠폰만 발급되고 나머지 요청은 모두 거부되는지 확인합니다.
  
* 시나리오 2 (수량 제한 정확성):

  * 가정: 한정된 수량(예: 500개)의 쿠폰에 대해 수천 명의 사용자가 동시에 발급을 요청합니다.
  
  * 검증: Race Condition 없이 정확히 한정된 수량의 쿠폰만 소진되고, 그 이후의 모든 요청은 실패 처리되는지 확인합니다.


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

* `coupon-consumer` 모듈의 CouponKafkaConsumerApplication.java
* `coupon-api` 모듈의 CouponApiApplication.java

[4] 모니터링 대시보드 확인 (Optional)

애플리케이션이 정상적으로 실행되면 아래 주소에서 모니터링 대시보드를 확인할 수 있습니다.

* Grafana: `http://localhost:3000` (ID/PW: admin/admin)
* Prometheus: `http://localhost:9090`


---

## Reference


* [[올리브영 테크블로그] 올리브영 초대량 쿠폰 발급 시스템 개선기](https://oliveyoung.tech/2024-12-11/oliveyoung-coupon-mess-issue/?keyword=쿠폰)

* [[인프런] 실습으로 배우는 선착순 이벤트 시스템](https://www.inflearn.com/course/선착순-이벤트-시스템-실습)

* [MAU 600만 서비스의 쿠폰함 성능 개선기: 백엔드 개발자의 고군분투](https://cwbeany.com/story/19)
