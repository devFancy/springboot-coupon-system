# Coupon System Design

이 프로젝트는 대규모 트래픽 환경에서 발생하는 동시성 이슈를 해결하고, 
대기열(Waiting Queue) 시스템을 중심으로 안정적인 쿠폰 발급 아키텍처를 설계하고 구현하며 얻은 기술적인 경험과 고민을 정리했습니다.

(이 문서는 V2 아키텍처를 중심으로 설명합니다. 초기 버전(V1)의 분산 락 기반 아키텍처에 대한 상세 설명은 [여기(링크)](./docs/coupon-issuance-api-v1.md)에서 확인하실 수 있습니다.)

> 관련 포스팅

* [MDC와 GlobalTraceId를 활용한 분산 추적](https://devfancy.github.io/SpringBoot-Distributed-Tracing-With-MDC/)

* [쿠폰 시스템 개선기: SETNX에서 Redisson RLock과 AOP를 활용한 분산락 적용](https://devfancy.github.io/SpringBoot-Coupon-System-Redisson/)

* [Prometheus와 Grafana로 Spring Boot 기반 모니터링 대시보드 구축하기](https://devfancy.github.io/SpringBoot-Monitoring-Prometheus-Grafana/)

---

## Project Overview

본 프로젝트는 블랙 프라이데이, 선착순 이벤트와 같이 
초당 수천 건 이상의 대규모 트래픽이 예상되는 상황에서 안정적인 쿠폰 발급을 목표로 설계된 시스템입니다. 
초당 수천 건의 동시 요청 속에서도 데이터 정합성을 보장하고, 사용자에게는 빠른 응답 속도를 제공하기 위해 비동기 메시지 큐와 대기열 시스템을 활용한 아키텍처를 구현했습니다.

> 주요 해결 과제

* 대용량 트래픽 제어: Race Condition을 방지하고 정확한 수량 관리를 통한 데이터 무결성 확보

* 응답 시간 최소화: 비동기 처리를 통해 사용자 API 응답 속도 향상

* 안정적인 장애 처리 및 부하 제어: Kafka와 DB로 유입되는 트래픽을 제어하여 시스템 전체의 안정성 확보

> 핵심 성과

* 대기열 기반 아키텍처를 통해 API 서버 최대 TPS 4,700 달성 (기존 락(Lock) 기반 구조 대비 62% 성능 향상) 및 선착순 발급 공정성 확보

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

대기열 시스템 기반의 쿠폰 발급 아키텍처는 API 서버의 역할을 명확히 분리하여 병목을 해소하고 확장성과 안정성을 고려한 구조입니다.

![](/docs/image/coupon-issue-architecture.png)

위 아키텍처의 실제 처리 흐름은 다음과 같은 3단계로 이루어집니다.

1단계 API 서버 (요청 접수)

* 사용자의 요청을 받으면, Redis Sorted Set에 나노초 단위의 시간값(score)과 함께 사용자 ID를 저장하고 즉시 202 Accepted로 응답합니다. 이 과정은 실제 발급 로직을 처리하지 않아 매우 가볍고, 높은 처리량(TPS)을 보장합니다.

2단계 비동기 발행

* API 서버 내의 @Scheduled 스레드가 주기적으로 Redis 대기열에서 일정량의 요청을 가져와 Kafka 토픽으로 발행합니다. 이 로직은 사용자의 요청 스레드와 완전히 분리되어 동작하며, Consumer로 유입되는 트래픽을 제어하는 역할을 합니다.

3단계 컨슈머(최종 처리)

* Kafka 컨슈머는 메시지를 받아 Redisson 분산 락을 획득한 후, DB 트랜잭션 내에서 최종적으로 쿠폰 발급 데이터를 저장합니다. 제어된 트래픽에 대해서만 락을 적용하므로 락 경합이 최소화됩니다.


### Failure Handling & Scheduled Retry (실패 처리 및 스케줄러 기반 재시도)

* 이전 단계에서 DB 저장 과정에서 장애가 발생하면, 데이터 유실을 방지하는 내결함성 로직이 동작합니다. 먼저 실패한 메시지 정보를 `FailedIssuedCoupon` 테이블에 기록합니다.

* 그 후, Spring `@Scheduled`로 동작하는 스케줄러가 주기적으로 이 테이블의 미처리 건을 조회하여 Kafka 토픽으로 재발행, 발급을 재시도합니다.

* 성공적으로 재처리된 쿠폰은 `isResolved` 상태로 변경하여 중복 발급을 막고 데이터의 최종 정합성을 확보합니다.

---

## 동시성 제어 및 트랜잭션 처리 전략

본 아키텍처는 다음과 같은 다단계 방어 전략을 통해 대규모 동시성 요청을 효과적으로 제어하고 데이터 정합성을 보장합니다.

[1단계] Redis Sorted Set을 이용한 요청 직렬화

  * 모든 발급 요청을 시간 순서대로 정렬되는 단일 자료구조(Sorted Set)에 저장함으로써, 물리적인 대기열을 만들어 요청 순서의 공정성을 확보합니다. 이는 동시 요청을 순차적인 처리 흐름으로 변환하는 첫 번째 관문입니다.

[2단계] 스케줄러를 통한 안정적인 부하 제어

  * 대기열에 쌓인 모든 요청을 한 번에 처리하는 것이 아니라, 스케줄러가 제어 가능한 만큼(batch size)만 꺼내어 Kafka로 전달합니다. 이를 통해 Consumer와 DB에 과부하가 걸리는 것을 원천적으로 방지합니다.

[3단계] 분산 락과 DB 트랜잭션을 이용한 최종 정합성 보장

  * 트랜잭션 처리: 최종 데이터 저장 로직은 `@Transactional`을 통해 원자적으로 실행됩니다. 
    DB 저장 중 문제가 발생하면 트랜잭션이 롤백되어 데이터 불일치를 막습니다.

  * 동시성 해결: 요청을 직접 처리하는 API 서버가 아닌, 제어된 트래픽을 처리하는 컨슈머 단계에서 락을 적용하여 락 점유 시간을 최소화하고 경합을 줄여 시스템 전체의 성능을 향상시킵니다. 
    이 락은 혹시 모를 비정상적인 중복 실행이 발생하더라도, 
    최종 단계에서 데이터의 일관성을 보장하는 중요한 안전장치 역할을 합니다.

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

* Redis: 대기열 시스템을 구현하고, 시스템 전반의 동시성을 조율하기 위해 도입했습니다.
    * 선착순 공정성 보장: 단순 List가 아닌 `Sorted Set`을 대기열로 선택했습니다. 나노초 단위의 시간값을 `score`로 활용하여 사용자의 요청 순서를 정밀하게 보장하고, 빠른 `ZADD`(O(logN)) 연산으로 API 서버의 병목을 최소화했습니다.
    * 최종 처리의 동시성 제어: 발급을 처리하는 컨슈머 단계에서는 `Redisson` 분산 락을 활용했습니다. 이를 통해 제어된 트래픽 내에서도 발생할 수 있는 동시성 이슈를 방지하고, 데이터의 최종 정합성을 확보하는 안전장치로 사용했습니다.

* Kafka: 대량의 쿠폰 발급 요청을 비동기적으로 처리하여 API 응답 시간을 최소화하고, 시스템 전체의 처리량을 향상시키기 위해 도입했습니다.
    * 데이터 유실 방지: 모든 발급 요청을 디스크 기반의 로그(Log)에 기록하여, 컨슈머 장애 시에도 데이터 유실 없이 안정적인 재처리를 보장합니다. 이는 `Failure Handling` 로직의 기반이 됩니다.
    * 안정적인 처리량 제어: Producer(스케줄러)와 Consumer를 완전히 분리하고, 제어된 양의 메시지만 전달함으로써 컨슈머가 과부하 없이 일정한 처리량을 유지하도록 시스템을 설계했습니다.

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
  
  * 검증: Race Condition 없이 정확히 500개의 쿠폰만 소진되고, 그 이후의 모든 요청은 실패 처리되는지 확인합니다.


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
