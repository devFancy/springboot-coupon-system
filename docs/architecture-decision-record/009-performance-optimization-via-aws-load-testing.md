# ADR 009: 로컬 환경의 한계를 극복하기 위한 AWS 기반 부하 테스트 및 아키텍처 최적화

DATE: 2025.12.18 (PR [#63](https://github.com/devFancy/springboot-coupon-system/pull/63) 참고)
- 관련 포스팅 링크: [AWS EC2 기반 부하 테스트를 진행하며 시스템 아키텍처 및 성능 개선하기](https://devfancy.github.io/spring-boot-coupon-system-performance-improvement/)

## 상태 (Status)

Accepted

## 맥락 (Context)

초기에는 로컬 Docker Compose 환경에서 부하 테스트를 진행했으나, 애플리케이션(API, Consumer)과 인프라(Kafka, DB, Redis)가 단일 머신의 자원(CPU, Memory, Disk I/O)을 공유함에 따라 자원 경합이 발생했다.
이로 인해 측정된 성능 지표의 신뢰성이 떨어졌으며, 실제 운영 환경과 유사한 분리된 인프라 환경에서의 검증이 필요했다.
또한, 테스트 과정에서 분산락(Redis) 구간이 전체 응답 시간의 일부분을 차지하는 병목 현상이 관측되었다.

## 결정 (Decision)

1. AWS 클라우드 기반의 테스트 환경 구축
   - 로컬 환경의 한계를 극복하기 위해 EC2(API, Consumer), RDS(MySQL), Redis, Kafka 등 전용 인파를 구성하여 실제 운영 환경과 유사한 격리된 환경을 구축한다.

2. K6를 활용한 점진적 부하 테스트
   - VUser(가상 사용자)를 단계적으로 늘려가며(100 ~ 2,000 TPS) 시스템의 한계 지점을 식별한다.

3. 병목 해소를 위한 아키텍처 최적화
   - 분산락 제거: Redis 락 획득/해제 비용이 전체 트랜잭션의 30% 이상을 차지함을 확인하고, 이를 DB 유니크 제약 조건 방식으로 대체한다.
   - Consumer 튜닝: Producer의 발행 속도를 Consumer가 따라가지 못하는 'Lag' 현상을 해소하기 위해 파티션 수와 Consumer 스레드 개수를 1:1로 매핑하여 병렬 처리량을 늘린다.

## 결과 (Consequences)

긍정적 효과 (Pros)
- 인프라 자원 간섭 없는 신뢰할 수 있는 성능 지표를 확보했으며, 최적화 이후 처리량(TPS)이 획기적으로 개선되었다.

부정적 효과 및 트레이드오프 (Cons)
- AWS 인프라 사용에 따른 비용이 발생하며, 테스트 환경 구축 및 배포 파이프라인 구성에 추가 공수가 들어간다.
