# ADR 006: Sentry 및 Loki를 통한 관측성 고도화

DATE: 2025.10.20 (PR [#51](https://github.com/devFancy/springboot-coupon-system/pull/51) 참고)

## 상태 (Status)

Accepted

## 맥락 (Context)

시스템이 coupon-api와 coupon-consumer로 분리됨에 따라, 에러 발생 시 어느 모듈에서 어떤 비즈니스 예외가 발생했는지 한눈에 파악하기 어려워졌다.
기존 Sentry 설정은 단순히 Exception Stack Trace만 보여주어 다음과 같은 한계가 있었다:

1. 필터링의 어려움: 동일한 CouponDomainException이라도 내부 에러 코드(예: `COUPON_NOT_FOUND`, `ALREADY_ISSUED`)에 따라 대응 우선순위가 다른데, 이를 Sentry 대시보드에서 즉시 구분할 수 없다.
2. 로그 파편화: 에러는 Sentry에 기록되지만, 해당 시점의 상세 흐름은 서버 내부 파일이나 Loki에 따로 존재하여 매칭이 어렵다.

## 결정 (Decision)

1. Sentry Sentry 전처리 콜백 구현
   - 로그 레벨 필터링: `LogLevel.INFO`에 해당하는 비즈니스 예외는 Sentry 전송을 차단하여 할당량을 관리하고 노이즈를 제거한다.
   - 비즈니스 태깅: 커스텀 예외 발생 시 내부에 정의된 `ErrorType`의 코드를 `errorCode`라는 태그로 주입하여 Sentry 대시보드에서의 검색 가시성을 높인다.

2. Grafana Loki 연동 강화
    - Logback 설정을 수정하여 애플리케이션 로그를 Loki로 전송한다.
    - 에러 발생 시 traceId, spanId, globalTraceId를 로그에 포함하여 로그 상에서 Sentry로 바로 이동할 수 있는 컨텍스트를 확보하여 [Error - Log - Metric]으로 이어지는 관측성 파이프라인을 구축한다.

## 결과 (Consequences)

긍정적 효과 (Pros)
- 장애 인지 속도 향상: 대시보드에서 "현재 어떤 종류의 비즈니스 에러가 급증하는가?"를 실시간으로 파악 가능하다.
- 효율적인 디버깅: 에러 코드 기반의 통계를 통해 클라이언트의 잘못된 요청인지, 서버의 로직 오류인지 빠르게 판별할 수 있다.

부정적 효과 및 트레이드오프 (Cons)
- 에러 전송 및 로그 수집을 위한 추가적인 네트워크 트래픽과 인프라 리소스가 소요된다.
