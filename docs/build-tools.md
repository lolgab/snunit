---
title: sbt and other build tools
---

The `snunit` command line tool is the shortest way to build an application, but it is not required.

## sbt

With sbt you don't need the CLI. The `sbt-snunit` plugin links `libunit` and embeds `unitd`
for you. It works with sbt 1.x and 2.x and `sbt-scala-native` 0.5.

```scala
// project/plugins.sbt
addSbtPlugin("com.github.lolgab" % "sbt-snunit" % "{{ site.snunit_version }}")

// build.sbt
lazy val app = project
  .enablePlugins(ScalaNativePlugin, SNUnitPlugin)
  .settings(libraryDependencies += "com.github.lolgab" %%% "snunit" % "{{ site.snunit_version }}") // %% on sbt 2
```

| Task or setting | What it does |
|---|---|
| `snunitBundle` | Builds the executable to deploy |
| `snunitBundleReleaseFast` | The same, linking with `nativeLinkReleaseFast` |
| `snunitBundleReleaseFull` | The same, linking with `nativeLinkReleaseFull` |
| `snunitPlatform` | The platform to bundle for. By default the machine you are building on |

On Linux the bundle is a copy of the linked binary with `unitd` appended, on macOS `unitd` is
embedded while linking. FreeUnit is downloaded the first time and cached in `~/.cache/snunit`,
as the CLI does.

## Other build tools

If you build the executable yourself, with Mill or scala-cli for example, `snunit` can add
`unitd` to it. You don't need scalino for this.

```bash
# Linux: appends unitd to the executable (add --platform linux-aarch64 etc. to bundle for another target)
snunit bundle target/scala-3.3.0/app -o app-bundled
```

The executable still needs `libunit.a` at link time.
`snunit link-flags` downloads FreeUnit and prints the linker options to use, one per line.

macOS is different: `unitd` can only be embedded while linking. There you don't run
`snunit bundle`, you pass the options printed by `snunit link-flags` to your build tool. They include
`-Wl,-sectcreate,__DATA,__unitd,<path to unitd>`.
