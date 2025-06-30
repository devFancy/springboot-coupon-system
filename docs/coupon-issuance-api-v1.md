# Ver.1 Coupon Issuance API

> 이 문서는 쿠폰 발급 시스템의 초기 버전(V1) 아키텍처를 설명합니다. 이 아키텍처의 한계점을 개선한 [최신 문서](https://github.com/devFancy/springboot-coupon-system/blob/main/README.md)로 이동하여 프로젝트의 전체 발전 과정을 확인하실 수 있습니다.

## System Architecture

쿠폰 발급 요청은 API 서버, Redis, Kafka, Consumer 애플리케이션을 거쳐 비동기적으로 처리됩니다.
이를 통해 API 서버의 부하를 최소화하고, 대량 요청을 안정적으로 큐에 적재하여 순차 처리합니다.

> Coupon issuance processing flow

아래는 쿠폰 발급 요청 시 Redis, Kafka, MySQL 등을 거쳐 발급 완료까지의 정상 처리 흐름에 대한 전체 프로세스를 요약한 도식입니다.

![](/docs/image/coupon-issue-api-v1-success-flow.png)


---

## Core Logic: Asynchronous Issuance & Concurrency Control

대규모 동시 요청 환경에서 쿠폰 발급의 데이터 정합성을 보장하기 위해, 아래와 같은 `3단계 동시성 제어 전략`을 핵심으로 설계했습니다.

* 1단계: 사용자 단위 요청 직렬화 (Redis Distributed Lock)

    * 동일 사용자의 중복 요청으로 인한 Race Condition을 원천 차단합니다.

* 2단계: 선착순 발급 제어 (Redis Atomic Operations)

    * DB 부하 없이 실시간으로 선착순 수량을 제어하고, 중복 발급 요청을 사전에 필터링합니다.

* 3단계: 최종 데이터 정합성 보장 (DB Unique Constraint)

    * 비정상적인 요청에 대비해 데이터베이스 레벨에서 데이터 무결성을 최종적으로 보장합니다.


### Detailed End-to-End Flow

위 전략을 바탕으로, 사용자 요청부터 최종 저장 및 실패 복구까지의 전체 흐름은 다음과 같습니다.

#### 1. Acquire Distributed Lock (사용자 요청 직렬화)

* 요청 접수 즉시, AOP(`@DistributedLock`)를 통해 사용자 ID 기반의 Redisson 분산 락을 획득합니다.
* 다중 서버 환경에서도 동일 사용자의 동시 요청을 효과적으로 차단합니다.

#### 2. Pre-validation with Redis (중복 및 수량 제어)

* 락 획득 후, Redis의 Atomic 연산을 통해 2차 검증을 수행합니다.
* 중복 발급 방지: `SADD` 명령으로 사용자의 요청 이력을 확인하여 중복 요청을 걸러냅니다.
* 수량 제어: `INCR` 명령으로 발급 수량을 증가시키며, 전체 수량 초과 시 `DECR`로 롤백하고 요청을 거부합니다.

#### 3. Asynchronous Handoff (비동기 처리)

* 모든 검증을 통과한 요청만 Apache Kafka `coupon.issue` 토픽으로 메시지를 발행합니다.
* API 서버는 즉시 사용자에게 응답을 반환하여, 대기 시간을 최소화합니다.

#### 4. Database Persistence (최종 저장)

* 별도의 Kafka Consumer 애플리케이션이 메시지를 구독하여 쿠폰 발급 내역을 MySQL에 저장합니다.
* `userId`와 `couponId`에 설정된 복합 유니크 키가 최후의 데이터 정합성을 보장합니다.

#### 5. Failure Handling & Scheduled Retry (실패 처리 및 스케줄러 기반 재시도)

* 이전 단계에서 DB 저장 과정에서 장애가 발생하면, 데이터 유실을 방지하는 내결함성 로직이 동작합니다. 먼저 실패한 메시지 정보를 `FailedIssuedCoupon` 테이블에 기록합니다.

* 그 후, Spring `@Scheduled`로 동작하는 스케줄러가 주기적으로 이 테이블의 미처리 건을 조회하여 Kafka 토픽으로 재발행, 발급을 재시도합니다.

* 성공적으로 재처리된 쿠폰은 `isResolved` 상태로 변경하여 중복 발급을 막고 데이터의 최종 정합성을 확보합니다.

![](/docs/image/coupon-failure-handling-flow.png)

#### Future Improvement: Move to Dead Letter Queue (최종 실패 처리)

* `retryCount`가 임계값(예: 3회)을 초과한 메시지는 향후 Dead Letter Queue(DLQ)로 이관하여 운영자가 수동 분석 및 조치할 수 있도록 시스템을 확장할 예정입니다.
