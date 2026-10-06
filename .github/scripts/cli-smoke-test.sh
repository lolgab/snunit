#!/usr/bin/env bash
# Builds a hello world with the snunit CLI and checks that the resulting single
# binary serves requests. Expects ./bin/snunit and SNUNIT_FREEUNIT_DIR.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"

workdir="$(mktemp -d)"
trap 'kill "${pid:-}" 2>/dev/null || true; rm -rf "$workdir"' EXIT

cat > "$workdir/Hello.scala" <<'SCALA'
import snunit.*

@main
def run =
  SyncServerBuilder
    .setRequestHandler(req =>
      req.send(StatusCode.OK, "Hello world!\n", Headers("Content-Type" -> "text/plain"))
    )
    .build()
    .listen()
SCALA

./bin/snunit package "$workdir/Hello.scala" -o "$workdir/app"

SNUNIT_PORT=18080 "$workdir/app" > "$workdir/log" 2>&1 &
pid=$!

for _ in $(seq 1 20); do
  if body="$(curl -fs --max-time 2 localhost:18080/)"; then
    [[ "$body" == "Hello world!" ]] && echo "OK" && exit 0
  fi
  sleep 0.5
done

echo "Smoke test failed. App log:" >&2
cat "$workdir/log" >&2
exit 1
