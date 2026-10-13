#!/usr/bin/env bash
# Makes the OTLP/HTTP receiver of the OpenTelemetry collector (used by Jaeger) serve through FreeUnit when it is started
# by it (NXT_UNIT_INIT set), sharing with the Jaeger query server the single handler FreeUnit gives to a program (see
# stack/unitmux). A copy of the version of the receiver Jaeger uses is patched and used instead of the downloaded one.
# Usage: patch-jaeger-otlp.sh <project dir>
# Expects the FreeUnit Go module in <project dir>/third_party/unit-go and unitmux in <project dir>/third_party/unitmux.
set -euo pipefail
project="$1"
cd "$project"
test -f third_party/unit-go/go.mod || { echo "third_party/unit-go missing" >&2; exit 1; }
test -f third_party/unitmux/go.mod || { echo "third_party/unitmux missing" >&2; exit 1; }
go mod download go.opentelemetry.io/collector/receiver/otlpreceiver
receiver="$(go list -m -f '{{.Dir}}' go.opentelemetry.io/collector/receiver/otlpreceiver)"
rm -rf third_party/otlpreceiver
cp -R "$receiver" third_party/otlpreceiver
chmod -R u+w third_party/otlpreceiver
python3 - <<'PY'
p = 'third_party/otlpreceiver/otlp.go'
s = open(p).read()
old = '''	var hln net.Listener
	if hln, err = httpCfg.ServerConfig.ToListener(ctx); err != nil {
		return err
	}
'''
assert old in s, 'otlpreceiver otlp.go changed: update the patch'
s = s.replace(old, '''	if os.Getenv("NXT_UNIT_INIT") != "" {
		// Modified by the snunit project (https://github.com/lolgab/snunit): when started by FreeUnit the requests
		// come through it, with the ones of the other components of the program, so there is no listener of our own.
		r.settings.Logger.Info("Serving HTTP through FreeUnit", zap.String("path", "/v1/"))
		unitmux.Handle("/v1/", r.serverHTTP.Handler)
		return nil
	}

	var hln net.Listener
	if hln, err = httpCfg.ServerConfig.ToListener(ctx); err != nil {
		return err
	}
''', 1)
s = s.replace('\t"net/http"\n', '\t"net/http"\n\t"os"\n', 1)
s = s.replace('\t"go.uber.org/zap"\n', '\t"github.com/lolgab/snunit/stack/unitmux"\n\t"go.uber.org/zap"\n', 1)
open(p, 'w').write(s)
PY
gofmt -w third_party/otlpreceiver/otlp.go
# The change as a patch (relative to the root of the project), for the archive.
tmp="$(mktemp -d)"
mkdir -p "$tmp/a/third_party" "$tmp/b/third_party"
cp -R "$receiver" "$tmp/a/third_party/otlpreceiver"
cp -R third_party/otlpreceiver "$tmp/b/third_party/otlpreceiver"
(cd "$tmp" && diff -ruN a b > "$OLDPWD/third_party/otlpreceiver.patch") || [ $? -eq 1 ]
chmod -R u+w "$tmp"
rm -rf "$tmp"
go mod edit -require=github.com/lolgab/snunit/stack/unitmux@v0.0.0 \
  -replace=github.com/lolgab/snunit/stack/unitmux=./third_party/unitmux \
  -replace=go.opentelemetry.io/collector/receiver/otlpreceiver=./third_party/otlpreceiver
