---
title: How it works
---

## One executable, two roles

When the executable is started directly it extracts the embedded `unitd` (from memory
with `memfd_create` on Linux, from a cache directory on macOS), writes a `conf.json` in a temporary
state directory, starts `unitd` and forwards signals to it. FreeUnit then starts
the same executable as an `external` application. When started by FreeUnit,
the executable just serves requests.

```
./hello ──starts──▶ unitd ──starts──▶ ./hello   (application process)
(launcher)         (FreeUnit)    ├──▶ ./hello   (application process)
                                 └──▶ ...
```

In these pages "the launcher" is the first process, the one you start.

You can also run the executable under a FreeUnit you installed and configured yourself. In that
case there is no launcher, and the [configuration](configuration.html) in the code is ignored.

## Sync and async

SNUnit has two different server implementations.

With `SyncServerBuilder` you need to call `.listen()` to start listening.
It is a blocking operation: the thread that calls it stays in the FreeUnit event loop
and can't do anything else while listening.
Moreover, all the request handlers need to respond directly and can't be implemented
using `Future`s or any other asynchronous mechanism since no `Future` will run, being
the thread stuck on the `listen()` event loop.

With [http4s](http4s.html) or [tapir-cats-effect](tapir.html) the server is automatically scheduled to run on the
cats-effect event loop, based on epoll/kqueue.
This allows you to complete requests asynchronously using whatever mechanism you prefer.
A process can accept multiple requests concurrently, allowing great parallelism.

## Background threads

The server can run other threads (periodic workers, file watchers, long polling) next to `listen()`.
They can allocate and run the garbage collector while the server waits for requests: `SyncServer` waits
for FreeUnit in a way the Scala Native GC knows about.

## Shutdown

On `SIGTERM`, `SIGINT` or `SIGHUP` the executable stops accepting connections and waits for the running requests
to finish, then exits. Open websocket connections count as running requests.

| Event | What happens |
|---|---|
| First signal | No new connections, the running requests finish, then the executable exits |
| `shutdownTimeout` passes (30 seconds by default) | The remaining requests are interrupted |
| Second signal | Stops without waiting |
| Third signal | Kills the server |

If the executable is killed with `SIGKILL`, or in any other way it can't handle, a small watchdog process
stops FreeUnit and removes the temporary files.
