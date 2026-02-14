# Vendored in-process NGINX Unit (embed mode)

This directory contains a **minimal in-process** implementation of the NGINX Unit application API (`nxt_unit_*`). It allows snunit to use the **same Scala API** (`unsafe.scala`, `Request`, `SyncServer`, etc.) while serving HTTP **directly in the application process**, without running the Unit daemon (`unitd`) or linking against `libunit.a`.

## Contents

- **nxt_unit_embed.c** – Standalone implementation of the `nxt_unit_*` API backed by a minimal HTTP/1 server (listen, accept, parse request, call your `request_handler`, send response). Provides the same symbols as `libunit` so your existing `@link("unit")` Scala code works unchanged.
- **Unit app headers** – Struct definitions and declarations that match the official Unit API so the embed implementation and Scala Native use the same layout:
  - `nxt_unit_typedefs.h`, `nxt_unit_sptr.h`, `nxt_unit_field.h`, `nxt_unit_request.h`, `nxt_unit_response.h`, `nxt_unit.h`
- **nxt_auto_config.h**, **nxt_version.h** – Minimal stubs for the embed build (no Unit `configure`).

## Vendoring from NGINX Unit

To refresh the Unit API headers from a local Unit tree (e.g. `/Users/lorenzo/scala/unit`), copy from `unit/src/` into this directory:

- `nxt_unit.h`, `nxt_unit_typedefs.h`, `nxt_unit_request.h`, `nxt_unit_response.h`, `nxt_unit_field.h`, `nxt_unit_sptr.h`, `nxt_unit_websocket.h`, `nxt_websocket_header.h`

Do **not** overwrite `nxt_auto_config.h` or `nxt_version.h` (snunit keeps minimal stubs). The embed uses `nxt_unit_init(init, host, port)` (3 args); Unit’s public API uses 1 arg and reads from `NXT_UNIT_INIT` env, so `nxt_unit.h` is adjusted for the embed. `NXT_UNIT_HASH_HOST` in `nxt_unit_field.h` is added for the embed’s Host-header parsing.

## Build

Scala Native compiles all `.c` (and `.cpp`) files under `src/main/resources/scala-native` (or `resources/scala-native` for the snunit library) and links them into the final binary. No extra build step is needed.

When using this vendored embed implementation:

1. **Do not** link against the external `libunit.a` (do not add `-lunit`). The symbols are provided by `nxt_unit_embed.c`. If you use the snunit Mill plugin, it adds `-lunit` for the “run with unitd” workflow; for in-process serving, **do not** use that plugin’s `nativeLinkingOptions` (or use a run target that does not link `libunit` and does not start `unitd`).
2. **Run the binary directly**: build your app with snunit as a dependency, then run the native binary (e.g. `./out` or `mill run` without the Unit plugin). The process will listen on the host/port passed to `nxt_unit_init(init, host, port)` (e.g. via `SyncServerBuilder.setHost` / `setPort` or `CEAsyncServerBuilder.setHost` / `setPort`).

The previous `unit_embed.c` / `unit_embed.h` (which depended on the full Unit core) have been removed; `nxt_unit_embed.c` is the single self-contained implementation and provides the full `nxt_unit_*` API.
