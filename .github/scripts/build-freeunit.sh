#!/usr/bin/env bash
# Builds FreeUnit's `unitd` and `libunit.a` and packages them in
# freeunit-<ref>-<os>-<arch>.tar.gz inside $OUT_DIR (default: ./dist), where <ref>
# is the first 8 characters of the git ref (commit or tag).
#
# The FreeUnit commit is pinned in .github/freeunit-ref (override with $FREEUNIT_REF).
# Keep it in sync with snunit-build-core/SNUnitBuild.scala (checked in CI).
#
# On Alpine (musl) it links everything statically and names the archive linux-musl-<arch>: `unitd` and the
# executables that embed it then run on any Linux, even in a FROM scratch image.
#
# Prerequisites (installed by the workflow): a C toolchain, make, pcre2, openssl, rust (OpenTelemetry).
# On Alpine also the static libraries: pcre2-static (libpcre2-8.a) and openssl-libs-static.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FREEUNIT_REF="${FREEUNIT_REF:-$(tr -d '[:space:]' < "$here/../freeunit-ref")}"
OUT_DIR="${OUT_DIR:-$PWD/dist}"

case "$(uname -s)" in
  Linux) if [[ -f /etc/alpine-release ]]; then os=linux-musl; else os=linux; fi ;;
  Darwin) os=macos ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) arch=x86_64 ;;
  arm64 | aarch64) arch=aarch64 ;;
  *) echo "Unsupported arch: $(uname -m)" >&2; exit 1 ;;
esac

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

curl -sfL "https://github.com/freeunitorg/freeunit/archive/${FREEUNIT_REF}.tar.gz" \
  | tar xz -C "$tmpdir" --strip-components=1

configure_args=(--openssl --otel)
if [[ "$os" == macos ]]; then
  openssl_prefix="$(brew --prefix openssl@3)"
  configure_args+=("--cc-opt=-I${openssl_prefix}/include" "--ld-opt=-L${openssl_prefix}/lib")
fi

if [[ "$os" == linux-musl ]]; then
  # Static unitd; the Rust OpenTelemetry library links the static OpenSSL.
  configure_args+=("--ld-opt=-static")
  export OPENSSL_STATIC=1
fi

(
  cd "$tmpdir"
  ./configure "${configure_args[@]}"
  make -j"$(getconf _NPROCESSORS_ONLN)" build/sbin/unitd build/lib/libunit.a
)

name="freeunit-${FREEUNIT_REF:0:8}-${os}-${arch}"
mkdir -p "$OUT_DIR/$name"
cp "$tmpdir/build/sbin/unitd" "$tmpdir/build/lib/libunit.a" "$OUT_DIR/$name/"
tar czf "$OUT_DIR/$name.tar.gz" -C "$OUT_DIR" "$name"
rm -rf "$OUT_DIR/$name"

echo "Built $OUT_DIR/$name.tar.gz"
