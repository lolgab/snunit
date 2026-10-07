#!/usr/bin/env bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FREEUNIT_REF="${FREEUNIT_REF:-$(tr -d '[:space:]' < "$here/../freeunit-ref")}"
PREFIX="${FREEUNIT_PREFIX:-/usr/local}"

sudo apt-get update
sudo apt-get install -y libuv1-dev libidn2-dev libpcre2-dev libssl-dev

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

curl -sL "https://github.com/freeunitorg/freeunit/archive/${FREEUNIT_REF}.tar.gz" \
  | tar xz -C "$tmpdir" --strip-components=1

(
  cd "$tmpdir"
  ./configure --openssl --otel
  make build/sbin/unitd build/lib/libunit.a
  sudo install -d "$PREFIX/bin" "$PREFIX/lib"
  sudo install -m 755 build/sbin/unitd "$PREFIX/bin/unitd"
  sudo install -m 644 build/lib/libunit.a "$PREFIX/lib/libunit.a"
)

unitd --version
