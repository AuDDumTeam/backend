#!/bin/bash
# hook 스크립트의 통과·차단 케이스를 확인한다. 실행: scripts/hooks/test-hooks.sh
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fail=0

git -C "$tmp" init -q -b develop
mkdir -p "$tmp/docs/records/sketches" "$tmp/src/main" "$tmp/src/test"
export CLAUDE_PROJECT_DIR="$tmp"

sketch() { printf -- '---\nissue: %s\nstatus: %s\n---\n' "$1" "$2" > "$tmp/docs/records/sketches/$1-x.md"; }
branch() { git -C "$tmp" checkout -q -B "$1" 2>/dev/null; }

# check <이름> <기대 종료 코드> <스크립트> <JSON>
check() {
  out=$(printf '%s' "$4" | "$here/$3" 2>&1); code=$?
  if [ "$code" = "$2" ]; then echo "통과: $1"; else echo "실패: $1 (기대 $2, 실제 $code) $out"; fail=1; fi
}
edit() { printf '{"cwd":"%s","tool_name":"Edit","tool_input":{"file_path":"%s/%s"}}' "$tmp" "$tmp" "$1"; }
agent() { printf '{"cwd":"%s","agent_type":"%s","tool_name":"%s","tool_input":{"file_path":"%s/%s","command":"%s"}}' "$tmp" "$1" "$2" "$tmp" "$3" "$4"; }

S=require-approved-sketch.sh
branch feature/9-demo
check "sketch 없음이면 통과" 0 $S "$(edit src/main/A.java)"
sketch 9 초안
check "초안이면 src/main 차단" 2 $S "$(edit src/main/A.java)"
check "초안이어도 src/test는 통과" 0 $S "$(edit src/test/A.java)"
check "초안이어도 docs는 통과" 0 $S "$(edit docs/a.md)"
sketch 9 승인됨
check "승인됨이면 src/main 통과" 0 $S "$(edit src/main/A.java)"
sketch 9 완료
check "완료여도 src/main 통과(리뷰 반영)" 0 $S "$(edit src/main/A.java)"
printf -- '---\nissue: 9\n---\n' > "$tmp/docs/records/sketches/9-x.md"
check "status 없는 옛 양식이면 통과" 0 $S "$(edit src/main/A.java)"
sketch 9 초안
branch chore/demo
check "chore 브랜치면 통과" 0 $S "$(edit src/main/A.java)"
branch develop
check "develop이면 이 hook은 통과(직접 커밋은 다른 장치가 막는다)" 0 $S "$(edit src/main/A.java)"
check "입력이 깨져 있으면 통과" 0 $S "not json"

T=restrict-test-writer.sh
check "test-writer가 src/test 쓰기는 통과" 0 $T "$(agent test-writer Write src/test/A.java '')"
check "test-writer가 _workspace 쓰기는 통과" 0 $T "$(agent test-writer Write _workspace/x/note.md '')"
check "test-writer가 src/main 쓰기는 차단" 2 $T "$(agent test-writer Edit src/main/A.java '')"
check "test-writer가 docs 쓰기는 차단" 2 $T "$(agent test-writer Write docs/a.md '')"
check "test-writer가 gradlew test 실행은 통과" 0 $T "$(agent test-writer Bash x './gradlew test --tests Foo')"
check "test-writer가 다른 Bash는 차단" 2 $T "$(agent test-writer Bash x 'echo hi > src/main/A.java')"
check "메인 스레드는 src/main 쓰기 통과" 0 $T "$(printf '{"cwd":"%s","tool_name":"Edit","tool_input":{"file_path":"%s/src/main/A.java"}}' "$tmp" "$tmp")"
check "다른 에이전트는 통과" 0 $T "$(agent code-reviewer Edit src/main/A.java '')"

exit $fail
