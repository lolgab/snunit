# Changelog

## 0.11.0

- Add the `snunit` command line tool (`snunit run`, `snunit package`) that builds a single executable embedding FreeUnit
- Applications start `unitd` themselves when run directly (configurable with `SNUNIT_PORT` and `SNUNIT_PROCESSES`)
- Remove the Sbt and Mill plugins in favor of the CLI
- The CLI is released separately from the library (`cli-v*` tags), with binaries for Linux and macOS (x86_64 and aarch64), `snunit bundle` and `snunit link-flags` to use it without scalino

## 0.1.1

- Implement plugins for Sbt and Mill to run apps locally
- Bump various dependencies

## 0.1.0

- Delete the `snunit-routes` module based on trail
- Rename the Tapir integration files to match Tapir's conventions
