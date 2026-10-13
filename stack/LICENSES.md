# Licenses of the observability stack

The `snunit` repository itself is under the Apache-2.0 license (see `../LICENSE`). The programs in the observability stack
are separate projects with their own licenses. They are **not** part of this repository: `snunit stack install`
downloads archives built by the CI of this repository (`.github/workflows/stack-binaries.yml`) from its GitHub
releases. This document explains what is in them and what we do to comply with the licenses.

*This is not legal advice. If you ship or host the stack in a product, have your own lawyer check it.*

## What is distributed

| Archive | Built from | License | Project |
|---|---|---|---|
| `snunit-stack-metrics-*` | Prometheus | Apache-2.0 | https://github.com/prometheus/prometheus (CNCF graduated) |
| `snunit-stack-traces-*` | Jaeger | Apache-2.0 | https://github.com/jaegertracing/jaeger (CNCF graduated) |
| `snunit-stack-logs-*` | VictoriaLogs | Apache-2.0 | https://github.com/VictoriaMetrics/VictoriaLogs |
| `snunit-stack-dashboards-*` | Perses | Apache-2.0 | https://github.com/perses/perses (CNCF sandbox) |

Versions are in `versions.env`. Each archive also contains:

- the Go modules compiled into the binary (mostly Apache-2.0, MIT, BSD, ISC; a few under the MPL-2.0, which is a file
  level license: those modules are not modified, their source is at the versions in `licenses/go-modules.txt`, and they
  are listed in `licenses/copyleft-notice.txt`)
- the npm packages of the web UI embedded in the binary (Prometheus, Jaeger and Perses; VictoriaLogs ships its UI
  already built in its repository)
- FreeUnit's Go module and `libunit` (Apache-2.0), linked into every program
- for the dashboards program, the plugins (data sources and panels) of the Perses project, unmodified, as published
  by it in the release of the same version (Apache-2.0). They are archives that carry their own notices

All of them are under the Apache-2.0 license, as the CNCF requires of its projects, so there is no strong copyleft
obligation (no GPL, AGPL, SSPL, BUSL...) and the license of this repository is compatible. `check-licenses.sh` and
`collect-npm-licenses.js` fail the build if a strong copyleft license shows up in the Go modules or in the production
npm packages, so a new upstream version can't bring one in without notice.

## Apache-2.0 obligations (section 4) and how they are met

| | |
|---|---|
| (a) give recipients a copy of the license | `UPSTREAM-LICENSE` in every archive |
| (b) modified files carry prominent notices | the changed files say so in a comment, the whole change is in `SNUNIT-MODIFICATIONS.patch` and described in `SNUNIT-SOURCE.md` |
| (c) keep copyright, patent, trademark and attribution notices in the source | the change doesn't remove any; the licenses of every dependency are in `licenses/` |
| (d) include the NOTICE file | `UPSTREAM-NOTICE` when the project has one |

## What we change

Each program is built from the unmodified upstream source of the pinned version plus a small change: when it is started
by FreeUnit it serves its HTTP API through FreeUnit (`unit.ListenAndServe`) instead of opening its own listener.

- `patches/*.patch` (Prometheus, Jaeger) and `patch-victorialogs.sh`, `patch-perses.sh`, `patch-jaeger-otlp.sh` (and `unitmux/`, our own small package) contain the change. They modify
  Apache-2.0 files, so they are under the same license. The build scripts (`build.sh`, `collect-*.sh`,
  `check-licenses.sh`) and the rest of this repository are Apache-2.0.
- The binaries are **modified builds** and are not the official releases. They are not made, endorsed or supported by
  the authors of the programs.

## Trademarks

Apache-2.0 does not grant any trademark right (section 6), and the Linux Foundation's guidelines
(https://www.linuxfoundation.org/legal/trademark-usage) say that a trademark "should not be used as part of your product
name" and allow factual statements like "<your product> for <mark>". The names *Prometheus*, *Jaeger* and *Perses* belong to
The Linux Foundation (CNCF projects), *VictoriaLogs* to VictoriaMetrics. So:

- the archives, the directories and the executables are named after what the program does (`metrics`, `traces`, `logs`,
  `dashboards`, `snunit-metrics`...), not after the project
- the documentation says, factually, what each one is built from, that it is a modified build, and that it is not endorsed
  or supported by the project or its owner (this text, `SNUNIT-SOURCE.md` and the release notes)
- the programs themselves still show their own names (their web UIs, their `--help`): that is the upstream software

If an owner's trademark policy asks for more, follow it and rename.

## What every archive contains

- `UPSTREAM-LICENSE`, `UPSTREAM-NOTICE`: the license and notice files of the program
- `licenses/go-modules/`, `licenses/go-modules.txt`: license files and the list (with versions) of the Go modules
- `licenses/npm/`: license files and `packages.tsv` of the npm packages of the web UI
- `licenses/copyleft-notice.txt`: the modules under weak copyleft licenses
- `licenses/freeunit/`: license and notice of FreeUnit
- `SNUNIT-MODIFICATIONS.patch`: our change to the upstream files, as a patch
- `SNUNIT-SOURCE.md`: version and commit of the upstream source, what was changed, the trademark notice

## Maintainers

- Update `versions.env`, the patches and `.github/stack-release` together. A new release tag (`stack-vN`) builds and
  publishes everything again, including the checksums and the licenses.
- Before adding a program, check its license. The CNCF only accepts Apache-2.0 for its own code and a short list of
  permissive licenses for dependencies (https://github.com/cncf/foundation/blob/main/policies-guidance/allowed-third-party-license-policy.md):
  copyleft licenses (GPL, AGPL, ...) are not on it and are not used here. And check its trademark policy before naming
  anything after it.
