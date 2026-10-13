#!/usr/bin/env bash
# Makes VictoriaLogs (which vendors VictoriaMetrics' lib/httpserver) serve its HTTP API through FreeUnit when it is
# started by it (NXT_UNIT_INIT set). Usage: patch-victorialogs.sh <project dir>
# Expects the FreeUnit Go module in <project dir>/third_party/unit-go.
set -euo pipefail
project="$1"
cd "$project"
test -f third_party/unit-go/go.mod || { echo "third_party/unit-go missing" >&2; exit 1; }
python3 - <<'PY'
import os, shutil
p = 'vendor/github.com/VictoriaMetrics/VictoriaMetrics/lib/httpserver/httpserver.go'
s = open(p).read()
old = '''	if err := s.s.Serve(ln); err != nil {
		if err == http.ErrServerClosed {'''
assert old in s, 'lib/httpserver changed: update the patch'
s = s.replace(old, '''	var err error
	if os.Getenv("NXT_UNIT_INIT") != "" {
		// Modified by the snunit project (https://github.com/lolgab/snunit): when started by FreeUnit the program
		// serves HTTP through it. FreeUnit owns the HTTP listener and passes the requests to us.
		err = unit.ListenAndServe("", s.s.Handler)
	} else {
		err = s.s.Serve(ln)
	}
	if err != nil {
		if err == http.ErrServerClosed {''', 1)
assert '\t"os"\n' in s.split(')')[0]
s = s.replace('\t"github.com/valyala/fastrand"\n', '\t"github.com/valyala/fastrand"\n\tunit "unit.nginx.org/go"\n', 1)
open(p, 'w').write(s)

dst = 'vendor/unit.nginx.org/go'
shutil.rmtree(dst, ignore_errors=True)
os.makedirs(dst)
src = 'third_party/unit-go'
for f in os.listdir(src):
    if f.endswith(('.go', '.c', '.h')):
        shutil.copy(os.path.join(src, f), dst)

replace = './third_party/unit-go'
m = open('vendor/modules.txt').read()
marker = '\n# github.com/valyala/fastrand '
assert marker in m
m = m.replace(marker, '\n# unit.nginx.org/go v0.0.0 => ' + replace + '\n## explicit\nunit.nginx.org/go' + marker, 1)
m += '# unit.nginx.org/go => ' + replace + '\n'
open('vendor/modules.txt', 'w').write(m)
PY
gofmt -w vendor/github.com/VictoriaMetrics/VictoriaMetrics/lib/httpserver/httpserver.go
go mod edit -require=unit.nginx.org/go@v0.0.0 -replace=unit.nginx.org/go=./third_party/unit-go
