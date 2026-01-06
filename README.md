#  🎁 쿠다닥 (Kudadak)

선착순 이벤트 시 대규모 트래픽 속에서도 안정성과 성능을 보장하는 쿠폰 발급 시스템

## Project Instruction

본 프로젝트는 선착순 이벤트와 같은 단시간에 트래픽이 급증하는 상황에서도 시스템 가용성을 유지하며 데이터 정합성을 보장하는 쿠폰 발급 시스템입니다.

사용자는 대규모 접속 상황에서도 대기 시간을 최소화하며 빠르고 정확하게 쿠폰을 발급받을 수 있습니다.

## Technologies

![](/docs/image/kudadak-tech-stack.png)

## Coupon Issuance Flow

![](/docs/image/kudadak-process-workflow.png)

## System Architecture

![](/docs/image/kudadak-system-architecture.png)

## Monitoring Architecture

![](/docs/image/kudadak-monitoring-architecture.png)

## Blog Posts

* 재처리 및 DLQ 적용: [시스템 장애를 처리하기 위해 재처리 및 DLQ 적용하기](https://devfancy.github.io/spring-boot-kafka-dlq/)

* 성능 개선: [AWS EC2 기반 부하 테스트를 진행하며 시스템 아키텍처 및 성능 개선하기](https://devfancy.github.io/spring-boot-coupon-system-performance-improvement/)

* 에러 추적: [Spring Boot에서 Sentry를 이용한 에러 추적 시스템 개선하기](https://devfancy.github.io/spring-boot-sentry/)

* 분산 추적: [MDC와 GlobalTraceId를 활용한 분산 추적](https://devfancy.github.io/SpringBoot-Distributed-Tracing-With-MDC/)

* 모니터링 구축: [Prometheus와 Grafana로 Spring Boot 기반 모니터링 대시보드 구축하기](https://devfancy.github.io/SpringBoot-Monitoring-Prometheus-Grafana/)

* 로그 추적: [Spring Boot 요청 흐름 추적: Logging Filter와 traceId 적용기](https://devfancy.github.io/SpringBoot-Logging-Filter/)
