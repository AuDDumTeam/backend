# AuDDum

연애 고민 선택형 투표 서비스 **AuDDum(어떰)** 의 백엔드. Java 25, Spring Boot 4.1, JPA, MySQL 8.4. 설계는 `docs/`에 있고, 이 파일에는 문서 지도 포인터와 하네스 포인터, 항상 지켜야 하는 최소 규칙만 둔다.

## 문서

문서는 `docs/index.md`에서 시작한다. 읽는 순서, 규칙의 정본, P·UC·OP·R 번호 범례, 문서 지도가 거기 있다. 기능 규칙은 `docs/features/`의 기능 페이지 하나가 정본이다. 문서는 필요한 **절만** 읽고 전체를 통째로 읽지 않는다.

## 하네스

이슈 작업은 `auddum-work` 스킬이 받는다. 커맨드 `/next`(다음 할 일), `/start`(이슈 시작·sketch 승인), `/build`(테스트·구현·리뷰), `/wrap`(정본 갱신·마무리), `/pr`, `/revise`(PR 코멘트)로도, 자연어로도 부른다. 작은 일(`small`)은 `/start`가 크기를 구분해 넘긴다. 라우트별 절차, 관문, 재진입 판정, 공통 규칙은 `.claude/skills/auddum-work/SKILL.md`에 있다.

- 구현은 sketch 승인 뒤 메인 스레드가 한다. 에이전트 5개(sketch-reviewer, test-writer, domain-reviewer, code-reviewer, fact-verifier)는 검증과 테스트만 하고, 리뷰어와 검증자는 읽기 전용이다.
- hook 2개가 승인 전 `src/main` 수정과 test-writer의 `src/test` 밖 쓰기를 막는다(`scripts/hooks/`). 진짜 관문은 CI와 GitHub 브랜치 보호다.
- 하네스가 바뀐 이력과 후속 목록은 `docs/records/하네스-회고.md`에 있다.

발동하지 않는 것: 개념 질문, 동작·규칙이 안 바뀌는 오타·한 줄 수정(대화에서 바로 고친다. 브랜치·PR까지 요청하면 `small`), PR 머지·배포, `main`·`develop` 직접 커밋·push, docs에 없는 정책·행위 추가, 요청 없는 선작성. 동작이나 규칙이 바뀌는 일은 한 줄이어도 이슈 없이 구현하지 않고 `/start`로 sketch부터 한다.

## 규칙(요약)

정본은 각 문서다. 이 요약은 어떤 작업에서든 지켜져야 해서 둔다.

- 기준은 docs다. 코드가 docs와 다르면 코드를 고친다. 동작이 바뀌면 같은 PR에서 docs를 고친다(`/wrap`).
- 브랜치는 git flow(`feature/{이슈번호}-{요약}` ← `develop` ← `main`, 이슈 없는 작은 일은 `chore/*`)이고 release 브랜치는 없다. `main`·`develop`에 직접 커밋·push하지 않고 force push하지 않는다(`docs/howto/개발-워크플로.md`).
- PR 머지는 사람만 한다(`gh pr merge`는 deny).
- 커밋 제목은 동사 원형·명사형으로 쓰고(`추가`, `수정`) 도메인이 아닌 변경은 scope를 생략한다. 로컬 중간 커밋은 PR 전에 논리 단위로 다시 묶는다(`docs/howto/개발-워크플로.md`).
- 경로를 명시해 스테이징한다. `git add -A`·`git add .`는 쓰지 않고, `.env`와 `.env.*`(`.env.example` 제외)는 절대 스테이징하지 않는다.
- 테스트 메서드 이름은 한글이고 `@DisplayName`은 쓰지 않는다(`docs/engineering/컨벤션.md`).
- 빌드·테스트: `./gradlew build`, `./gradlew test`

## 구성

| 종류 | 위치 | 개수 |
|---|---|---|
| 스킬 | `.claude/skills/auddum-work/` | 1 |
| 커맨드 | `.claude/commands/` | 6 |
| 에이전트 | `.claude/agents/` | 5 |
| hook | `scripts/hooks/` 스크립트, `.claude/settings.json` 연결 | 2 |
| 중간 산출물 | `_workspace/<run-id>/` (run-id = 현재 브랜치 이름에서 `feature/`를 뗀 것, `/`는 `-`로) | 실행마다 |
| 최종 산출물 | 레포 작업 트리(브랜치 커밋)와 GitHub PR | 실행마다 |

실행 모드는 서브에이전트 모드로 고정되어 있다(`.claude/settings.json`의 `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=0`).
