# ADR 010: 대용량 트래픽 대응을 위한 인프라 스케일 아웃 및 고가용성 구성

DATE: 2025.12.23 (PR [#65](https://github.com/devFancy/springboot-coupon-system/pull/65) 참고)

## 상태 (Status)

Accepted

## 맥락 (Context)

코드 레벨의 최적화를 마쳤으나, 단일 서버 인스턴스로는 물리적인 CPU/Memory 한계로 인해 목표로 하는 대용량 트래픽(수만 TPS)을 감당하기 어렵다.
또한, 단일 장애 지점(SPOF)을 제거하여 특정 인스턴스 장애 시에도 서비스가 지속될 수 있는 구조가 필요하다.

## 결정 (Decision)

1. Kafka Consumer 확장
   - Kafka 파티션 개수를 늘리고, 이에 맞춰 Consumer 인스턴스(컨테이너)를 증설하여 메시지 처리 병목을 해소한다.

2. 인프라 고가용성(HA) 확보
   - Kafka 클러스터링: Kafka를 단일 브로커가 아닌 멀티 브로커 클러스터로 구성하여, 브로커 장애 시에도 리더 선출을 통해 데이터 유실 없이 서비스를 유지한다.
   - 리소스 격리: 각 컨테이너별로 CPU와 Memory 제한(Limit/Reservation)을 설정하여 특정 모듈의 자원 독점이 전체 시스템에 영향을 주지 않도록 관리한다.

## 결과 (Consequences)

긍정적 효과 (Pros)
- 트래픽 증가 시 서버 인스턴스만 추가하면 되는 유연한 확장성을 확보했다. 특정 서버 다운 시에도 전체 서비스 중단으로 이어지지 않는다.

부정적 효과 및 트레이드오프 (Cons)
- 다중 서버 환경이 되면서 로그가 분산되므로 [ADR 006](./006-enhance-observability-with-sentry-and-loki.md)의 중앙 집중식 로깅(Loki) 및 모니터링의 중요성이 더욱 커졌다.
