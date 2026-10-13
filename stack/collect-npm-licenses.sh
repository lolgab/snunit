#!/usr/bin/env bash
# Copies the license and notice files of the production npm dependencies of a project into <out>.
# Usage: collect-npm-licenses.sh <npm|pnpm> <project dir> <out dir>
# The dependencies must be installed. Fails on strong copyleft licenses.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tool="$1"; dir="$2"; out="$3"
mkdir -p "$out"
cd "$dir"
case "$tool" in
  npm) paths="$(npm ls --omit=dev --all --parseable)" ;;
  pnpm) paths="$(pnpm -r ls --prod --depth Infinity --parseable)" ;;
  *) echo "unknown package manager $tool" >&2; exit 1 ;;
esac
printf '%s\n' "$paths" | sort -u | node "$here/collect-npm-licenses.js" "$out" "$dir"
