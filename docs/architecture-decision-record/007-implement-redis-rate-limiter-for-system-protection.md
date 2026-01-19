# ADR 007: Redis 기반 Rate Limiter를 활용한 컨슈머 유량 제어

DATE: 2025.11.07 (PR [#58](https://github.com/devFancy/springboot-coupon-system/pull/58) 참고)

## 상태 (Status)

Accepted

## 맥락 (Context)

Kafka 도입으로 인해 API 서버와 컨슈머 서버가 분리되었으나, 여전히 최종 저장소인 RDB의 처리 능력에는 한계가 있다. [Issue #52](https://github.com/devFancy/springboot-coupon-system/issues/52) 논의 결과 다음과 같은 잠재적 위험이 확인되었다.

- DB 부하 전파: 대규모 이벤트 발생 시 Kafka 토픽에 수백만 건의 메시지가 순식간에 쌓인다. 컨슈머가 이를 제한 없이 가져와 처리할 경우, DB 커넥션 풀 고갈 및 락 경합으로 인해 전체 시스템 장애로 이어질 수 있다.
- 클러스터 단위 유량 제어 필요: 컨슈머 인스턴스가 여러 대일 때, 각 인스턴스가 개별적으로 속도를 조절하는 것만으로는 전체 DB에 가해지는 총 부하를 제어하기 어렵다.
- 운영 안정성: 시스템이 감당 가능한 최적의 TPS를 초과하지 않도록 보장하면서, 유실 없이 안정적으로 메시지를 처리해야 한다.

## 결정 (Decision)

1. Redisson RRateLimiter 기반의 `Token Bucket` 알고리즘 채택
    - 단순한 로컬 라이브러리가 아닌 Redis 기반의 분산 Rate Limiter를 사용한다.
    - RateType.OVERALL 설정: 여러 컨슈머 인스턴스가 존재하더라도 Redis를 통해 전체 허용량(Total TPS)을 공유하도록 하여, 클러스터 전체의 총 처리량을 정밀하게 제어한다.

2. 컨슈머 측 `acquire()` 방식의 지연 처리 (Backpressure)
    - CouponIssueConsumer에서 비즈니스 로직을 수행하기 직전 `rateLimiter.acquire(1)`를 호출한다.
    - 토큰이 없는 경우 스레드는 블로킹(Blocking) 상태로 대기하며, 토큰이 보충되는 즉시 처리를 재개한다. 이를 통해 Kafka로부터 메시지를 가져오는 속도를 자연스럽게 늦추는 백프레셔 기제를 구현한다.

## 결과 (Consequences)

긍정적 효과 (Pros)
- 데이터베이스 보호: DB가 감당할 수 있는 최적의 속도로만 쓰기 작업을 수행하여 시스템 안정성을 극대화한다.
- 자원 효율성: 컨슈머 서버가 가용 자원을 모두 소진하여 다운되는 현상을 방지하고, 일정한 응답 시간을 보장한다.
- 유연한 운영: 서버 재시작 없이 totalMaxTps 설정값을 조정함으로써 실시간으로 시스템 처리량을 튜닝할 수 있다.

부정적 효과 및 트레이드오프 (Cons)
- Kafka 컨슈머 랙(Lag) 발생: 유입 트래픽이 처리 속도보다 빠를 경우 Kafka 토픽에 메시지가 쌓이게 된다. 이는 시스템 보호를 위해 의도된 지연이지만, 실시간성이 중요한 비즈니스에서는 지연 시간이 문제가 될 수 있다.
- Redis 의존성: Rate Limiter의 동작이 Redis에 의존하므로, Redis 장애 시 컨슈머의 처리가 멈추거나 제한 없이 동작할 수 있는 위험이 있다.