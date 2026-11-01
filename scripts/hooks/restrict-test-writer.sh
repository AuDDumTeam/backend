#!/bin/bash
# PreToolUse(Edit|Write|MultiEdit|Bash): test-writer가 도는 동안 쓰기를 src/test와 작업 산출물로 제한한다.
# 에이전트 파일의 tools로는 Bash를 주면서 쓰기만 막을 수 없어서 hook으로 강제한다.
# test-writer가 아닌 호출(메인 스레드, 다른 에이전트)은 항상 통과시킨다.
input=$(cat)
agent=$(jq -r '.agent_type // empty' <<<"$input" 2>/dev/null)
[ "$agent" = "test-writer" ] || exit 0
tool=$(jq -r '.tool_name // empty' <<<"$input" 2>/dev/null)
cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
proj=${CLAUDE_PROJECT_DIR:-${cwd:-.}}

if [ "$tool" = "Bash" ]; then
  cmd=$(jq -r '.tool_input.command // empty' <<<"$input" 2>/dev/null)
  case "$cmd" in
    "./gradlew compileTestJava"*|"./gradlew test"*) exit 0 ;;
  esac
  echo "차단: test-writer는 Bash로 ./gradlew compileTestJava와 ./gradlew test만 실행한다. 실행하려던 명령: $cmd" >&2
  exit 2
fi

file=$(jq -r '.tool_input.file_path // empty' <<<"$input" 2>/dev/null)
[ -z "$file" ] && exit 0
rel=${file#"$proj"/}
case "$rel" in
  src/test/*|_workspace/*) exit 0 ;;
esac
echo "차단: test-writer는 src/test/**와 _workspace/** 밖을 쓰지 않는다($rel). 구현 코드는 메인 스레드 몫이다." >&2
exit 2
