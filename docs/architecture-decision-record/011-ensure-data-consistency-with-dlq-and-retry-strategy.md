# ADR 011: 최종 데이터 정합성 보장을 위한 Kafka DLQ 및 재처리 전략

DATE: 2024.12.30 (PR [#66](https://github.com/devFancy/springboot-coupon-system/pull/66) 참고)
- 관련 포스팅 링크: [시스템 장애를 처리하기 위해 재처리 및 DLQ 적용하기](https://devfancy.github.io/spring-boot-kafka-dlq/)

## 상태 (Status)

Accepted

## 맥락 (Context)

비동기 시스템에서 일시적인 네트워크 장애나 DB 데드락 등으로 인해 Consumer 처리가 실패할 수 있다. 단순히 에러 로그만 남기고 넘어가면 고객의 쿠폰 발급 요청이 유실되는 치명적인 데이터 정합성 문제가 발생한다.

## 결정 (Decision)

1. 신뢰성 있는 재시도(Retry) 전략 수립
   - `DefaultErrorHandler`와 `ExponentialBackOff`를 적용하여, 실패 시 지수 간격으로 최대 N회 재시도를 수행한다.
   - 일시적인 장애는 이 단계에서 대부분 해소될 것으로 기대한다.

2. Dead Letter Queue (DLQ) 및 실패 이력 보존
   - 재시도 후에도 실패한 메시지는 DLQ 토픽으로 격리하여 정상적인 메시지 흐름을 방해하지 않도록 한다.
   - DLQ Consumer에서도 처리에 실패할 경우, 최종적으로 `CouponIssueFailedEvent` 엔티티로 변환하여 DB에 영구 저장한다.

3. 모니터링 가시성 확보
   - Micrometer와 Prometheus를 연동하여 'DLQ 전송 총 횟수', 'Consumer Lag', '처리 실패율' 등의 핵심 지표를 Grafana 대시보드에 시각화하고 알림을 설정한다.

## 결과 (Consequences)

긍정적 효과 (Pros)
- 시스템의 어떤 장애 상황에서도 사용자의 요청 데이터가 증발하지 않고 DB에 기록되어 추후 복구가 가능하다.

부정적 효과 및 트레이드오프 (Cons)
- 실패 이력 테이블 관리를 위한 스토리지 비용이 발생하며, 운영자가 주기적으로 실패 건을 확인하고 재처리해야 하는 운영 프로세스가 필요하다.
