#!/usr/bin/env bash
# Stop-hook: houdt Claude tegen zolang er codewijzigingen zijn zonder
# bijgewerkt projectlogboek (docs/PENGUINCLOUD.md).
# Afhankelijkheden: bash en git; jq is optioneel (er is een fallback).
set -u

LOG="docs/PENGUINCLOUD.md"
input="$(cat 2>/dev/null || true)"

# 1. stop_hook_active=true -> altijd laten stoppen (voorkomt eindeloze lus).
if command -v jq >/dev/null 2>&1; then
  active="$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null || echo false)"
else
  if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
    active=true
  else
    active=false
  fi
fi
[ "$active" = "true" ] && exit 0

# 2. Naar de root van de repository; geen git -> niets te controleren.
root="${CLAUDE_PROJECT_DIR:-}"
[ -n "$root" ] || root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$root" ] && cd "$root" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# 3. Basisbranch bepalen: PENGUINCLOUD_BASE_BRANCH, anders de standaardbranch
#    van origin, anders origin/main of origin/master.
base="${PENGUINCLOUD_BASE_BRANCH:-}"
if [ -z "$base" ]; then
  base="$(git symbolic-ref -q --short refs/remotes/origin/HEAD 2>/dev/null || true)"
fi
if [ -z "$base" ]; then
  for b in origin/main origin/master main master; do
    if git rev-parse -q --verify "$b^{commit}" >/dev/null 2>&1; then base="$b"; break; fi
  done
fi

# 4. Gewijzigde bestanden: commits van deze branch + werkboom + nieuwe bestanden.
changed="$(
  {
    if [ -n "$base" ] && mb="$(git merge-base "$base" HEAD 2>/dev/null)"; then
      git diff --name-only "$mb" HEAD 2>/dev/null
    fi
    git diff --name-only HEAD 2>/dev/null
    git diff --name-only --cached 2>/dev/null
    git ls-files --others --exclude-standard 2>/dev/null
  } | sed '/^$/d' | sort -u
)"

[ -z "$changed" ] && exit 0
code="$(printf '%s\n' "$changed" | grep -vxF "$LOG" || true)"
[ -z "$code" ] && exit 0
printf '%s\n' "$changed" | grep -qxF "$LOG" && exit 0

printf '%s\n' '{"decision":"block","reason":"Werk eerst docs/PENGUINCLOUD.md bij met de wijzigingen van deze sessie."}'
exit 0
