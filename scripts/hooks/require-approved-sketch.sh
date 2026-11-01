#!/bin/bash
# PreToolUse(Edit|Write|MultiEdit): src/main 수정 전에 현재 브랜치의 sketch가 승인됐는지 확인한다.
# 막는 경우는 하나다: feature/<이슈>-… 브랜치에 그 이슈의 sketch가 있고, status가 있는 sketch가 모두 초안이다.
# 그 밖(sketch 없음, 옛 양식, 브랜치 이름이 다름, 입력을 읽지 못함)은 통과시킨다. 작업을 멈추는 쪽의 오류가 더 크다.
input=$(cat)
file=$(jq -r '.tool_input.file_path // empty' <<<"$input" 2>/dev/null)
cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
[ -z "$file" ] && exit 0
proj=${CLAUDE_PROJECT_DIR:-${cwd:-.}}
rel=${file#"$proj"/}
case "$rel" in src/main/*) ;; *) exit 0 ;; esac

branch=$(git -C "${cwd:-$proj}" branch --show-current 2>/dev/null)
[[ "$branch" =~ ^feature/([0-9]+)- ]] || exit 0
issue=${BASH_REMATCH[1]}

statuses=""
for f in "$proj"/docs/records/sketches/"$issue"-*.md; do
  [ -f "$f" ] || continue
  s=$(sed -n '2,/^---$/s/^status:[[:space:]]*//p' "$f" | head -1)
  [ -n "$s" ] && statuses="$statuses $s"
done
[ -z "$statuses" ] && exit 0
case "$statuses" in *승인됨*|*완료*) exit 0 ;; esac

echo "차단: 이슈 #${issue}의 sketch가 아직 승인되지 않았다(status:$statuses). 승인 전에는 src/main을 고치지 않는다. start 라우트에서 사용자 승인을 받은 뒤 status를 승인됨으로 바꾸고 다시 시도한다." >&2
exit 2
