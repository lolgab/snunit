Metrics, traces, logs and dashboards programs built to run inside FreeUnit next to an SNUnit application. Install them
with `snunit stack install`.

**These are modified builds, not the official releases.** They are built from the upstream sources with a small change
(when started by FreeUnit they serve HTTP through it) and are not made, endorsed or supported by their authors. The
project names and marks belong to their owners (The Linux Foundation, VictoriaMetrics) and are used only to say what each
build is made from.

| Archive | Built from | License |
|---|---|---|
| `snunit-stack-metrics-*` | Prometheus (CNCF graduated) | Apache-2.0 |
| `snunit-stack-traces-*` | Jaeger v2 (CNCF graduated) | Apache-2.0 |
| `snunit-stack-logs-*` | VictoriaLogs | Apache-2.0 |
| `snunit-stack-dashboards-*` | Perses (CNCF sandbox) | Apache-2.0 |

Every archive contains the license and notice files of the program, of its Go modules and of the npm packages of its web
UI (`licenses/`), the change we made (`SNUNIT-MODIFICATIONS.patch`) and `SNUNIT-SOURCE.md`. `SHA256SUMS` has the checksums
of all the archives.
