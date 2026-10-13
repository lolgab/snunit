#!/usr/bin/env bash
# Makes Perses serve its HTTP API through FreeUnit when it is started by it (NXT_UNIT_INIT set). The HTTP server is in
# github.com/perses/common: a copy of the version Perses uses is patched and used instead of the downloaded one.
# Usage: patch-perses.sh <project dir>
# Expects the FreeUnit Go module in <project dir>/third_party/unit-go.
set -euo pipefail
project="$1"
cd "$project"
test -f third_party/unit-go/go.mod || { echo "third_party/unit-go missing" >&2; exit 1; }
go mod download github.com/perses/common
common="$(go list -m -f '{{.Dir}}' github.com/perses/common)"
rm -rf third_party/perses-common
cp -R "$common" third_party/perses-common
chmod -R u+w third_party/perses-common
python3 - <<'PY'
p = 'third_party/perses-common/echo/server.go'
s = open(p).read()
old = '''			if err := s.e.Start(s.addr); err != nil {
				logrus.WithError(err).Info("http server stopped")
			}'''
assert old in s, 'perses/common echo/server.go changed: update the patch'
s = s.replace(old, '''			var err error
			if os.Getenv("NXT_UNIT_INIT") != "" {
				// Modified by the snunit project (https://github.com/lolgab/snunit): when started by FreeUnit the
				// program serves HTTP through it. FreeUnit owns the HTTP listener and passes the requests to us.
				err = unit.ListenAndServe("", s.e)
			} else {
				err = s.e.Start(s.addr)
			}
			if err != nil {
				logrus.WithError(err).Info("http server stopped")
			}''', 1)
imports = s[s.index('\nimport (\n'):]
imports = imports[:imports.index('\n)\n')]
if '\t"os"\n' not in imports + '\n':
    s = s.replace('\nimport (\n', '\nimport (\n\t"os"\n', 1)
s = s.replace('\t"github.com/sirupsen/logrus"\n', '\t"github.com/sirupsen/logrus"\n\tunit "unit.nginx.org/go"\n', 1)
open(p, 'w').write(s)
PY
gofmt -w third_party/perses-common/echo/server.go
# The change as a patch (relative to the root of the project), for the archive.
tmp="$(mktemp -d)"
mkdir -p "$tmp/a/third_party" "$tmp/b/third_party"
cp -R "$common" "$tmp/a/third_party/perses-common"
cp -R third_party/perses-common "$tmp/b/third_party/perses-common"
(cd "$tmp" && diff -ruN a b > "$OLDPWD/third_party/perses-common.patch") || [ $? -eq 1 ]
chmod -R u+w "$tmp"
rm -rf "$tmp"
go mod edit -require=unit.nginx.org/go@v0.0.0 -replace=unit.nginx.org/go=./third_party/unit-go \
  -replace=github.com/perses/common=./third_party/perses-common
