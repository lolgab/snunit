#!/usr/bin/env bash
# Copies the license and notice files of every Go module a program is built from into <out>.
# Usage: collect-licenses.sh <project dir> <package> <out dir>
set -euo pipefail
project="$1"; package="$2"; out="$3"
cd "$project"
mkdir -p "$out"
go list -deps -f '{{with .Module}}{{.Path}}|{{.Dir}}{{end}}' "$package" 2>/dev/null | sort -u | while IFS='|' read -r path dir; do
  [ -z "$path" ] && continue
  # Vendored projects have no module directory.
  if [ -z "$dir" ] || [ ! -d "$dir" ]; then dir="vendor/$path"; fi
  [ -d "$dir" ] || continue
  for f in "$dir"/LICEN[CS]E* "$dir"/COPYING* "$dir"/NOTICE* "$dir"/PATENTS; do
    if [ -f "$f" ]; then
      mkdir -p "$out/$path"
      cp "$f" "$out/$path/"
    fi
  done
done
