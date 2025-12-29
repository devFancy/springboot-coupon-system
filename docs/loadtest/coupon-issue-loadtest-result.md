# 쿠폰 발급 시스템 - 부하 테스트 결과 보고서

대규모 트래픽 상황에서 쿠폰 발급 시스템의 안정성과 성능을 검증하고 최적화한 과정을 기술한다.

최종 성과: (최대) 1,500 TPS (안정성 최우선 설정 기준)

> 핵심 달성

- 인프라 확장: 인프라, 컨슈머, DB 서버 분리 및 수직/수평 확장을 통한 병목 해소
- 데이터 무결성 확보: Kafka 클러스터 구축 및 멱등적 처리 설정을 통해 데이터 유실 없는 아키텍처 구축
- 성능 개선: 애플리케이션, DB, Redis, Kafka 전 계층의 코드 리팩터링 및 설정값 최적화
- 모니터링 강화: 각 서버별 전용 모니터링 대시보드 구축을 통한 실시간 병목 지점 파악 및 시스템 가시성 확보

> (최종) System Architecture

![](/docs/loadtest/image/kudadak-system-architecture-4.png)

> (최종) Monitoring Architecture

![](/docs/loadtest/image/kudadak-monitoring-architecture-2.png)

아래는 초기 아키텍처 구축부터 부하 테스트를 통한 점진적인 개선 과정을 정리한 내용이다.


---

## 1. 초기 아키텍처 검증 (VU 500 ~ 5,000)

초기 단계에서는 단일 인스턴스(`t3.medium`) 환경에서 기본 성능을 측정하고, 자원 경합 및 병목 로직을 식별하는 데 집중했다.

### (초기) 쿠폰 발급 프로세스

![](/docs/loadtest/image/kudadak-process-workflow-1.png)

### (초기) System Architecture

![](/docs/loadtest/image/kudadak-system-architecture-1.png)

#### 1-1. 부하 테스트 결과

- 사용자 500명 기준
- Rate Limiter: 100

> K6 결과 - p95 응답 시간: 75.16ms, 안정적인 처리량 확인.

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-1-1.png)

> API Server 결과 - 최대 TPS: 500

![](/docs/loadtest/image/coupon-issue-load-test-api-server-1-1.png)

> Consumer Server 결과 - 최대 TPS: 110

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-1-1.png)

저부하 상황에서는 지연 없이 정상 동작함을 검증했다.

#### 1-2. 부하 테스트 결과

- 사용자 1,000명 기준
- Rate Limiter: 200

> K6 결과 - p95 응답 시간: 2.95s

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-1-2.png)

> API Server 결과 - 최대 TPS: 400

![](/docs/loadtest/image/coupon-issue-load-test-api-server-1-2.png)

> Consumer Server 결과 - 최대 TPS: 130

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-1-2.png)

단일 인스턴스 내 리소스 경합으로 인해 레이턴시가 이전 대비 약 40배(0.075s -> 2.95s) 가까이 상승하며 시스템이 포화점에 도달했음을 확인했다.

#### 1-3. 부하 테스트 결과

- 사용자 5,000명 기준
- Rate Limiter: 200

> K6 결과 - p95 응답 시간: 6.75s

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-1-3.png)

> API Server 결과 - 최대 TPS: 900

![](/docs/loadtest/image/coupon-issue-load-test-api-server-1-3.png)

> Consumer Server 결과 - 최대 TPS: 95

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-1-3.png)

고부하 상황에서 API 서버 대비 컨슈머 처리 속도가 현저히 낮아 Consumer Lag이 누적되었으며, 이는 시스템 전반의 가용성을 저하시키는 핵심 병목임을 파악했다.


---

## 2. 인프라 확장 및 병목 지점 파악하여 성능 개선 (VU 7,000 ~ 10,000)

사용자 증가에 따른 리소스 포화를 분석하고, 서버 인스턴스 분리 및 코드 리팩터링(분산락 제거), 인프라 튜닝 값을 통해 처리량을 극대화했다.

### System Architecture 개선

기존 단일 인스턴스 서버에서 아래와 같이 인스턴스 서버를 분리함

- API 서버 - t3.medium
- 인프라 서버(Redis, Kafka, DB) - t3.medium
- 컨슈머 서버 - t3.medium -> 단일 인스턴스 내 컨슈머 서버 2대로 상향 (1대 -> 2대)
- 모니터링 서버 - t3.small
- 부하 테스트 서버 - t3.medium

### Monitoring Architecture 개선

![](/docs/loadtest/image/kudadak-monitoring-architecture-1.png)

### 주요 개선 작업

- Consumer 서버에서 멱등성 보장 및 분산락 제거
    - 문제 지점: 초기에는 중복 발급 방지를 위해 Redisson 분산락을 적용했으나, 락 획득/해제 과정의 네트워크 RTT가 전체 처리량의 병목이 됨을 확인했다.
    - 해결 방안: 복잡한 상태 변경이 없는 단순 `INSERT` 로직임을 고려하여 분산락을 제거하고, DB 유니크 제약조건을 활용하여 데이터 정합성을 보장했다.
    - 구현: `DataIntegrityViolationException` 발생 시 '이미 처리된 요청'으로 간주하고 `Acknowledgment`를 수행하여 불필요한 재시도를 차단했다.

이를 바탕으로 아래와 같이 쿠폰 발급 프로세스를 개선했다.

![](/docs/loadtest/image/kudadak-process-workflow-2.png)

- DB 튜닝 - 단일 인스턴스 자원을 효율적으로 사용하기 위해 HikariCP 설정을 최적화했다.
    - 캐시 적용을 통해 동일 쿼리의 파싱 비용을 줄여 DB CPU 부하를 줄였고, 타임아웃 처리를 짧게 설정하여 장애 발생 시 커넥션 대기로 인한 연쇄적인 스레드 고갈을 방지했다.

![](/docs/loadtest/image/kudadak-coupon-db-hikaricp-1.png)

- 컨슈머 서버 - 스레드 및 파티션 수 조정 및 Config 값 수정
    - 컨슈머 스레드 수: 3 -> 10
    - 파티션 수: 3 -> 20
    - max.poll.records: 500 -> 2000

- 모니터링 지표 개선
    - Consumer Lag 지표 수집 에러 해결

- 모니터링 지표 추가
    - kafka-exporter 추가

### 2-1. 부하 테스트 결과 (VU 7,000 / Rate Limiter 1,000)

> K6 결과 - p95 응답 시간: 5.55s

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-2-1.png)

> API Server 결과 - 최대 TPS: 1,450

![](/docs/loadtest/image/coupon-issue-load-test-api-server-2-1.png)

> Consumer Server 결과 - 최대 TPS: 810

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-2-1.png)

### 추가 개선 작업

기존 인프라 서버 내에서 DB의 CPU 사용률이 매우 높아 리소스 간섭이 발생하는 것을 확인했다.

이를 해결하기 위해 DB 서버를 인프라 서버에서 분리하여 전용 서버(t3.small)로 구축하고 아래와 같이 시스템 아키텍처를 개선했다.

![](/docs/loadtest/image/kudadak-system-architecture-2.png)

- API 서버 - 톰캣 튜닝
    - tomcat.thread.max: 500 -> 200

- 컨슈머 스레드 및 파티션 수 조정
    - 컨슈머 스레드 수: 10 -> 15
    - 파티션 수: 20 -> 30

- 컨슈머 Config 값 수정
    - fetch.min.bytes: 1MB -> 1KB
    - fetch.max.wait.ms: 1초 -> 0.2초

- DB 튜닝
    - HikariCP maximum-pool-size: 10 -> 15

#### 2-2. 부하 테스트 결과 (DB 분리 후 VU 7,000 / Rate Limiter 1,300)

> K6 결과 - p95 응답 시간: 2.49s

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-2-2.png)

> API Server 결과 - 최대 TPS: 2,500

![](/docs/loadtest/image/coupon-issue-load-test-api-server-2-2.png)

> Consumer Server 결과 - 최대 TPS: 1,250

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-2-2.png)

> DB 모니터링 결과 분석

![](/docs/loadtest/image/coupon-issue-db-monitoring-2-2.png)

- 컨슈머 서버의 CPU가 100%에 도달했으며, DB 역시 CPU 사용률이 192.51%를 기록하며 2-core 자원을 모두 사용하고 있음을 확인했다.
- 또한 컨슈머 서버의 TPS가 API 서버의 절반 수준에 머물어 Consumer Lag이 급격히 증가했다.
- 이에 컨슈머 서버의 TPS 향상을 위해 인스턴스 대수를 2대로 늘리고, 연산 병목 해소를 위해 DB 서버를 `t3.xlarge` (4-core)로 스케일 업하기로 결정했다.

### 추가 개선 작업: DB 스케일 업 및 컨슈머 수평 확장

물리적 자원 한계를 극복하기 위해 DB 사양을 상향하고 컨슈머를 확장했다.

- 컨슈머 서버 수평 확장(Scale-out): 컨슈머 서버 인스턴스를 1대에서 2대로 증설했다. (`t3.medium`)
- DB 서버 수직 확장(Scale-up): 연산 병목 해소를 위해 DB 서버를 기존 `t3.small`(2-core) 에서 `t3.xlarge`(4-core)로 업데이트했다.

이를 기반으로 시스템 아키텍처를 개선하고 세부 설정을 조정했다.

![](/docs/loadtest/image/kudadak-system-architecture-3.png)

- 컨슈머 스레드 및 파티션 수 조정
    - 컨슈머 스레드 수: 15 -> 20
    - 파티션 수: 30 -> 40

- DB 튜닝
    - HikariCP maximum-pool-size: 15 -> 20

- Redis 튜닝: 커넥션 풀 및 타임아웃 설정을 튜닝하여 통신 효율을 높였다.
    - connectionPoolSize: 65
    - connectionMinimumIdleSize: 12
    - connectTimeout: 3000
    - timeout: 1000

- Rate Limiter 값 수정: 1,300 -> 2,700

- 모니터링 지표 개선

#### 2-3. 부하 테스트 결과 (VU 10,000 / Rate Limiter 2,700)

> API Server 결과 - 최대 TPS: 2,500

![](/docs/loadtest/image/coupon-issue-load-test-api-server-2-3.png)

> Consumer Server 결과 - 최대 TPS: 2,500

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-2-3.png)

- 이전 대비 Consumer Lag이 많이 개선되었다. (이전: 10만건 -> 이후: 1,000건 단위)

---

## 3. 시스템 안정성을 위해 고가용성 확보 및 성능 개선 (VU 7,000 ~ 10,000)

서비스 안정성을 위해 성능을 일부 희생하더라도 데이터 유실을 방지하는 운영 환경 수준의 가용성 아키텍처를 구축했다.

### 주요 개선 작업

- 카프카 클러스터 고가용성(HA) 확보
    - Broker: 브로커 수를 1개에서 2개로 증설하여 단일 장애점(SPOF) 제거 및 가용성 확보
        - 메모리 부족 현상을 막기 위해 (t3.medium 기준) 브로커 1대당 512M 메모리 제한
    - 컨슈머 스레드 및 파티션 수, DB HikariCP 조정
        - 컨슈머 스레드 수: 15 -> 30
        - 파티션 수: 30 -> 60
        - HikariCP - maximum-pool-size: 20 -> 30
    - Topic의 replication.factor=2, min.insync.replicas=2 설정을 통해 멀티 브로커 환경에서의 데이터 무결성 보장

- 프로듀서 Config 값 수정
    - batch.size: 64KB -> 32KB
    - linger.ms: 10 -> 5

- 컨슈머 Config 값 수정
    - fetch.min.bytes: 1MB -> 1KB
    - max.poll.records: 2000 -> 1000

- 기존 API/Consumer 서버에서 각 서버별로 모니터링 대시보드 구축 및 지표 개선
    - API Server
    - Infra Server
    - Consumer Server
    - Database Server

이를 바탕으로 아래와 같이 시스템 및 모니터링 아키텍처를 개선했다.

> System Architecture

![](/docs/loadtest/image/kudadak-system-architecture-4.png)

> Monitoring Architecture

![](/docs/loadtest/image/kudadak-monitoring-architecture-2.png)

### 부하 테스트 결과 ( VU 7,000 / Rate Limiter 2,700)

> K6 결과 - p95 응답 시간: 5.97s

![](/docs/loadtest/image/coupon-issue-load-test-k6-result-3-1.png)

> API Server 결과 - 최대 TPS: 1,500

![](/docs/loadtest/image/coupon-issue-load-test-api-server-3-1.png)

> Consumer Server 결과 - 최대 TPS: 1,500

![](/docs/loadtest/image/coupon-issue-load-test-consumer-server-3-1.png)

API 서버와 비슷한 TPS로 Consumer Lag 증가에 대한 문제는 해결되었다.

> Infra Server - Monitoring

![](/docs/loadtest/image/coupon-issue-load-test-infra-server-monitoring-3-1.png)

> Database Server - Monitoring

![](/docs/loadtest/image/coupon-issue-load-test-db-server-monitoring-3-1-1.png)

![](/docs/loadtest/image/coupon-issue-load-test-db-server-monitoring-3-1-2.png)

### 성과 및 결론

- Kafka 프로듀서 및 컨슈머의 Config 튜닝을 통해 대량 유입 상황에서도 Consumer Lag을 실시간으로 해소하는 구조를 완성했다.
- 브로커 이중화 및 복제 설정을 통해 성능(2,700 TPS -> 1,500 TPS)을 일부 희생하는 대신, 운영 환경 수준의 데이터 안정성과 고가용성을 확보했다.
- 현재 시스템의 최대 한계는 API 서버의 CPU 자원 고갈임이 확인되었으나, 컨슈머와 DB 서버는 여전히 80% 미만의 여유 자원을 보유하고 있어 API 서버 확장 시 선형적인 성능 향상이 가능할 것으로 판단된다.

### 향후 개선 계획

- 현재 CPU 사용률 100%에 도달한 API 서버를 증설하여 부하를 분산하고, p95 응답 시간을 목표치인 3초 이내로 단축할 계획이다.
- API 서버의 TPS 유입량에 맞춰 컨슈머 서버의 Rate Limiter 값을 유동적으로 조정함으로써, 시스템 전반의 처리 효율을 극대화할 계획이다.
- 아직 컨슈머 서버와 DB 서버는 여전히 80% 미만의 CPU 여유 자원을 보유하고 있음을 확인했다.
- 따라서 API 서버 확장 시 전체 시스템의 TPS가 선형적으로 향상될 수 있는 구조적 여력이 충분하다고 판단한다.
