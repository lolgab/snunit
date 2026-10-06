#!/usr/bin/env bash
# Builds FreeUnit's `unitd` and `libunit.a` and packages them in
# freeunit-<version>-<os>-<arch>.tar.gz inside $OUT_DIR (default: ./dist).
#
# Prerequisites (installed by the workflow): a C toolchain, make, pcre2, openssl.
set -euo pipefail

FREEUNIT_VERSION="${FREEUNIT_VERSION:-1.36.1}"
OUT_DIR="${OUT_DIR:-$PWD/dist}"

case "$(uname -s)" in
  Linux) os=linux ;;
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

curl -sfL "https://github.com/freeunitorg/freeunit/archive/refs/tags/${FREEUNIT_VERSION}.tar.gz" \
  | tar xz -C "$tmpdir" --strip-components=1

configure_args=(--openssl --otel)
if [[ "$os" == macos ]]; then
  openssl_prefix="$(brew --prefix openssl@3)"
  configure_args+=("--cc-opt=-I${openssl_prefix}/include" "--ld-opt=-L${openssl_prefix}/lib")
fi

(
  cd "$tmpdir"
  ./configure "${configure_args[@]}"
  make -j"$(getconf _NPROCESSORS_ONLN)" build/sbin/unitd build/lib/libunit.a
)

name="freeunit-${FREEUNIT_VERSION}-${os}-${arch}"
mkdir -p "$OUT_DIR/$name"
cp "$tmpdir/build/sbin/unitd" "$tmpdir/build/lib/libunit.a" "$OUT_DIR/$name/"
tar czf "$OUT_DIR/$name.tar.gz" -C "$OUT_DIR" "$name"
rm -rf "$OUT_DIR/$name"

echo "Built $OUT_DIR/$name.tar.gz"
