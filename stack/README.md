# Observability stack inside FreeUnit

Builds of Prometheus (metrics), Jaeger (traces), VictoriaLogs (logs) and Perses (dashboards) to run as FreeUnit `external` applications, with the
[FreeUnit Go module](../freeunit/go) (`unit.ListenAndServe`). Under FreeUnit (`NXT_UNIT_INIT` set) each one serves its
HTTP API through FreeUnit, otherwise it behaves as upstream. All of them are Apache-2.0; the first two are CNCF graduated
projects, Perses is a CNCF project too, and VictoriaLogs is the only one outside the CNCF (the CNCF has no project that
stores logs).

Users don't build them: CI builds and releases them and the CLI installs them.

```bash
snunit stack install           # downloads them into ~/.cache/snunit/stack (checksums verified)
snunit run --stack App.scala   # run the app with the stack, dashboards on http://localhost:3000
```

or in the code: `Observability("my-service", stack = Some(Stack()))`, see `snunit.config.Stack`.

| Role (archive and directory) | Built from | Where |
|---|---|---|
| `metrics` | Prometheus | http://localhost:9090 |
| `traces` | Jaeger | http://localhost:16686 (OTLP/HTTP on 4318) |
| `logs` | VictoriaLogs | http://localhost:9428/select/vmui |
| `dashboards` | Perses | http://localhost:3000 |

Ports: the four above are served through FreeUnit on `bindAddress` (`127.0.0.1` by default). Jaeger also opens the OTLP/HTTP
receiver on 4318 (`bindAddress`): it is not served through FreeUnit because only Jaeger's query server is patched, not the
OTLP receiver of its collector. The programs also bind ports of their own on `127.0.0.1` behind FreeUnit (dashboards, traces
HTTP and gRPC, logs): the launcher asks for any free port (0), so they can't clash. All of them are fields of
`snunit.config.Stack`; the application keeps port 8080.

The archives, the directories and the executables are named after the role (`snunit-stack-metrics-*`, `snunit-metrics`...), not after the project: the Linux Foundation asks not to use its marks in the name of a product, and the same is safer for the others.

## Layout

| File | |
|---|---|
| `versions.env` | upstream versions of the programs |
| `patches/` | the change to Prometheus and Jaeger |
| `patch-victorialogs.sh`, `patch-perses.sh` | the change to VictoriaLogs (vendored `lib/httpserver`) and Perses (a copy of `perses/common`) |
| `build.sh` | clone, patch, build (including the web UIs), collect the licenses, check them and package one program |
| `collect-licenses.sh`, `collect-npm-licenses.sh`, `collect-npm-licenses.js` | license files of the Go modules and the npm packages in a binary |
| `check-licenses.sh` | fails on strong copyleft licenses (AGPL, GPL, SSPL, BUSL...) |
| `SOURCE.md.in` | `SNUNIT-SOURCE.md` of every archive |
| `RELEASE_NOTES.md` | text of the GitHub release |
| `LICENSES.md`, `licenses/` | what is distributed under which license and what we do about it |

## Releasing

1. Change `versions.env` and the patches (`build.sh <program>` builds one locally, see its header for the environment).
2. Pick a new tag `stack-vN`, put it in `.github/stack-release` and in `StackRelease` of
   `snunit-build-core/SNUnitBuild.scala` (CI checks they agree).
3. Push the tag: `.github/workflows/stack-binaries.yml` builds every program for Linux x86_64, Linux aarch64, macOS aarch64
   and macOS x86_64 and publishes the archives and `SHA256SUMS` in the release.
4. Release the CLI (`cli-v*` tag) to point users to it.

## Limits

FreeUnit speaks HTTP/1.1 only, so gRPC doesn't go through it. Jaeger's OTLP/HTTP receiver keeps its own port too: its collector is not patched, only the query server (serving it would also need the receiver and the UI to share the one handler FreeUnit gives to a program). Each
program runs with a single process. The patches have to be redone for new upstream versions. None of the programs is configured with a login: they listen on the loopback interface.
