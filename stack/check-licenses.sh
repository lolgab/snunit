#!/usr/bin/env bash
# Fails if a license text of the Go modules in an archive directory is a strong copyleft license (AGPL, GPL, SSPL,
# BUSL). Lists the weak copyleft ones (MPL, LGPL) in licenses/copyleft-notice.txt.
# Usage: check-licenses.sh <archive directory>
set -euo pipefail
dir="$1"
go_licenses="$dir/licenses/go-modules"
[ -d "$go_licenses" ] || { echo "no $go_licenses" >&2; exit 1; }
strong="$(grep -rlE 'GNU AFFERO GENERAL PUBLIC LICENSE|GNU GENERAL PUBLIC LICENSE|Server Side Public License|Business Source License' "$go_licenses" || true)"
if [ -n "$strong" ]; then
  echo "Strong copyleft license texts found, review them before shipping:" >&2
  echo "$strong" | sed "s|$go_licenses/|  |" >&2
  exit 1
fi
{
  echo "Modules under weak copyleft licenses (file level: only the files of those modules are covered)."
  echo "They are not modified: their source is available from the repositories of the modules, at the versions of"
  echo "go-modules.txt."
  echo
  grep -rlE 'Mozilla Public License|GNU LESSER GENERAL PUBLIC LICENSE|GNU Library General Public' "$go_licenses" \
    | sed -e "s|$go_licenses/||" -e 's|/[^/]*$||' | sort -u || true
} > "$dir/licenses/copyleft-notice.txt"
