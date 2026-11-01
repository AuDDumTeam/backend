---
issue: 155
sources: [이슈 #155, product/로드맵.md, howto/로컬-환경.md, howto/배포.md, engineering/인증.md, engineering/설계-결정.md]
features: [없음]
ids: []
decisions: []
status: 완료
---

# /actuator/health가 응답하도록 설정하고 테스트한다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 정본으로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

로드맵 M1 완료 기준 "헬스 체크가 응답한다"가 남아 있다(이슈 #155). 배포 파이프라인의 헬스 체크(#21)와 Actuator 포트 폐쇄(#23)가 이 동작을 전제로 한다. 지금은 `spring-boot-starter-actuator` 의존성만 있고 `management.*` 설정과 테스트가 없다.

소스와 달라진 점: 없음. 로컬 환경 문서는 이미 `curl /actuator/health`가 `UP`이라고 안내한다. 지금 실제로 응답하는지는 첫 테스트(red)로 확인한다.

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| `GET /actuator/health`는 로그인 없이 200으로 응답하고 본문은 `status`가 `UP`이다 | 인증이 필요하거나 응답하지 않으면 배포 헬스 체크(#21)가 실패한다 | 로드맵 M1 완료 기준 · 헬스 체크 테스트 |
| 헬스 응답은 `status`만 보이고 `components` 같은 상세 정보가 없다 | DB·디스크 같은 내부 정보가 외부에 노출된다 | 질문 2 · 헬스 체크 테스트 |
| 헬스는 DB 연결 상태를 포함한다. DB가 끊기면 503 `DOWN`이다 | DB가 죽은 앱을 `UP`으로 보고 배포가 진행된다 | 질문 1 · 기본 동작 유지(설정 없음). H2 테스트로는 확인하지 못하고 #21 배포 환경에서 확인한다 |
| Actuator 웹 노출은 `health` 하나다. 그 밖의 엔드포인트(`/actuator/env`, `/actuator/info`, `/actuator/prometheus`)는 404다. 지표 노출은 #23이 배포 문서 방침대로 넓힌다 | 환경·지표 정보가 노출된다 | 질문 3 · 노출 범위 테스트 |

## 건드리는 것

- 고친다: 공통 `application.yaml`의 `management.*`, 헬스 체크 테스트(신규), 로드맵 M1 완료 기준, 로컬 환경·배포 문서의 관련 서술
- 건드리지 않는다: 기능 API, 도메인, `SecurityConfig`의 인증 정책(현재 모든 요청 통과), 지표 노출·Actuator 포트 분리와 네트워크 폐쇄(#23)

## 접근

공통 `application.yaml`에 `management.endpoints.web.exposure.include=health`와 `management.endpoint.health.show-details=never`를 명시해 노출 범위를 설정으로 고정하고, 응답과 노출 범위를 테스트로 확인한다. 설정이 공통 파일에 있으므로 이 규칙은 prod 한정이 아니라 전 환경 규칙이고, H2 테스트가 같은 설정을 그대로 검증한다. 보안 설정은 바꾸지 않는다(이미 모든 요청이 통과한다).

검토한 대안:

- 별도 관리 포트(`management.server.port`)로 분리: #23(포트 폐쇄)의 범위이고, 분리하면 배포 헬스 체크(#21)가 다른 포트를 보게 된다. 이 이슈에서는 하지 않는다.
- 커스텀 `/health` 컨트롤러: 표준 Actuator가 이미 있어 이중으로 만들게 된다.

질문과 답:

- 질문 1. 헬스 체크가 DB 연결까지 확인해도 되는가? 답: 포함한다. 기본 동작(DB가 끊기면 503 `DOWN`)을 유지한다. 배포 롤백 판정(#21)이 DB 순간 장애에 반응하는 문제는 #21에서 재시도 횟수로 다룬다(사용자, 2026-11-01).
- 질문 2. 응답에 `status`만 보이면 충분한가? 답: `status`만 보인다. 모니터링(#23)이 상세를 필요로 하면 그때 사설 IP에서만 보이게 한다(사용자, 2026-11-01).
- 질문 3. 노출 범위를 이 이슈에서 어디까지 정하는가? 답: `health` 하나만 열고, 지표(`prometheus`) 노출은 #23이 `배포.md`의 방침(지표는 노출하고 포트는 네트워크로 닫는다)대로 넓힌다(사용자, 2026-11-01).
- 질문 4. `management.*` 설정은 어디에 두는가? 답: 공통 `application.yaml`(사용자, 2026-11-01).
- 질문 5. 테스트 범위는? 답: H2 + `MockMvc`만. 실제 MySQL 연결 상태는 배포 환경(#21)에서 확인한다(사용자, 2026-11-01).
- 질문 6. 구현 중 발견: `src/test/resources/application.yaml`이 메인 `application.yaml`을 가려서 메인의 `management.*`를 테스트가 읽지 못한다. 어떻게 검증하는가? 답: 테스트 `application.yaml`에도 같은 `management.*`를 복제한다. 한쪽만 고치면 어긋나도 테스트가 통과하는 한계를 안고 간다(사용자, 2026-11-01).
- 질문 7. 구현 중 발견: 기본 헬스 응답에 `groups: [liveness, readiness]`가 함께 나와 "키가 `status` 하나뿐"을 지키지 못한다. 답: `management.endpoint.health.probes.enabled=false`로 없앤다(환경 변수로 실험해 `status`만 남는 것을 확인했다. 규칙은 그대로이고 구현 세부다).

## 구조

- 설정: `src/main/resources/application.yaml`
- 테스트: `src/test/java/org/kwakmunsu/auddum/support/` 아래 헬스 체크 테스트(기존 `support` 패키지 구조를 따른다)
- 문서: `docs/product/로드맵.md`, `docs/howto/로컬-환경.md`, `docs/howto/배포.md`

## 순서와 테스트

테스트는 `@SpringBootTest` + `MockMvc`로, 기본(local) 프로파일의 H2에서 돈다. 설정이 공통 파일에 있으므로 prod 프로파일 전용 테스트는 만들지 않는다.

1. 헬스 체크 테스트를 먼저 쓴다. 이미 통과하면 현재 동작의 확인이므로 그 사실을 기록한다.
   - 인증 없는 `GET /actuator/health`가 200이고 `status`가 `UP`이다.
   - 응답 본문의 키가 `status` 하나뿐이다(`components`가 없고 설정 값 같은 다른 키도 없다). 값 전체를 비교하지 않고 키 기준으로 본다. 이후 헬스 그룹이나 probes가 생겨도 깨지지 않게 한다.
2. 노출 범위 테스트: `/actuator/env`, `/actuator/info`, `/actuator/prometheus`가 404다.
3. `management.*` 설정을 명시한다.
4. 문서를 고친다(로드맵 M1 완료 기준, 로컬 환경, 배포).
5. `./gradlew build`.

## 지킬 규칙

- 컨벤션: 테스트 이름은 한글, `@DisplayName`을 쓰지 않는다.
- 설계 결정: 정본에 없는 정책을 만들지 않는다. 지표 노출은 배포 문서의 방침을 따르며 이 이슈에서는 정하지 않는다.

## 안전장치

- 상세 정보 노출: `show-details`를 `never`로 고정하고 테스트로 확인한다.
- 의도치 않은 엔드포인트 노출: 노출 목록을 `health` 하나로 명시하고, 다른 엔드포인트가 404인지 테스트한다.
- 설정 값 비밀: 응답 본문의 키가 `status` 하나뿐인 것을 테스트로 확인한다.

## 결과

공통 `application.yaml`에 `management.*`를 명시해 웹에 여는 Actuator를 `health` 하나로 고정하고, 응답을 `status` 하나로 만들었다(상세 숨김, probes 끔). 테스트 `ActuatorHealthTest`(5개)가 200과 `UP`, 키가 `status` 하나뿐인 것, `env`·`info`·`prometheus`의 404를 확인한다. 로드맵 M1 완료 기준과 로컬 환경·배포 문서를 맞췄다.

계획과 달라진 점:

- 테스트 리소스의 `application.yaml`이 메인을 가려서 메인의 `management.*`를 테스트가 읽지 못했다. 사용자 결정(질문 6)으로 테스트 yaml에도 같은 설정을 복제했다.
- 기본 응답에 `groups`가 함께 나와 `probes.enabled=false`를 더했다(질문 7).
- 리뷰 반영으로 테스트를 `MockMvc`에서 주변 코드와 같은 `MockMvcTester`로 바꾸고, 중복 단언을 없앴다.
- 설정 위치를 "공통 `application.yaml`"로 정했으나 테스트용 복제가 추가됐다.

한계와 후속:

- 테스트 yaml이 메인을 복제한 것이라 한쪽만 고치면 어긋나도 테스트가 통과한다. #23에서 노출 범위를 넓힐 때 두 yaml을 동기화하는 테스트(메인 yaml을 파일로 읽어 비교)를 도입할지 본다.
- DB가 끊기면 503 `DOWN`이 되는지는 H2로 확인하지 못했다. 배포 환경(#21)에서 확인한다(로드맵 #21 항목에 옮겨 적었다).
- `/actuator/prometheus` 404 확인은 지금 지표 의존성이 없어서 `include`를 넓혀도 404일 수 있다. #23에서 의존성이 들어올 때 다시 본다.
- 실제 시험 중 찾은 하네스 개선점은 #131~#136의 시험 정리에서 따로 다룬다.
