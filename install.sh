#!/usr/bin/env bash
# Installs the latest `snunit` CLI release.
#
#   curl -fsSL https://raw.githubusercontent.com/lolgab/snunit/main/install.sh | bash
#
# Environment:
#   SNUNIT_CLI_VERSION  version to install, e.g. 0.1.0 (default: the latest release)
#   INSTALL_DIR         where to put the binary (default: ~/.local/bin)
set -euo pipefail

repo="lolgab/snunit"
install_dir="${INSTALL_DIR:-$HOME/.local/bin}"

case "$(uname -s)" in
  Linux) os=unknown-linux-musl ;;
  Darwin) os=apple-darwin ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) arch=x86_64 ;;
  arm64 | aarch64) arch=aarch64 ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
triple="$arch-$os"

version="${SNUNIT_CLI_VERSION:-}"
if [[ -z "$version" ]]; then
  # Releases of other kinds (freeunit-*) live in the same repository, so look for the newest cli-v* tag.
  version="$(curl -fsSL "https://api.github.com/repos/$repo/releases?per_page=100" \
    | grep -o '"tag_name": *"cli-v[^"]*"' | head -1 | sed 's/.*"cli-v\(.*\)"/\1/')"
  [[ -n "$version" ]] || { echo "Cannot find a snunit CLI release" >&2; exit 1; }
fi

archive="snunit-$version-$triple.tar.gz"
url="https://github.com/$repo/releases/download/cli-v$version/$archive"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "Downloading $url"
curl -fL --progress-bar -o "$tmpdir/$archive" "$url"
curl -fsSL -o "$tmpdir/$archive.sha256" "$url.sha256"
(
  cd "$tmpdir"
  expected="$(awk '{print $1}' "$archive.sha256")"
  if command -v sha256sum >/dev/null; then actual="$(sha256sum "$archive" | awk '{print $1}')"
  else actual="$(shasum -a 256 "$archive" | awk '{print $1}')"; fi
  [[ "$expected" == "$actual" ]] || { echo "Checksum mismatch for $archive" >&2; exit 1; }
  tar xzf "$archive"
)

mkdir -p "$install_dir"
install -m 755 "$(find "$tmpdir" -type f -name snunit | head -1)" "$install_dir/snunit"
echo "Installed snunit $version to $install_dir/snunit"
case ":$PATH:" in *":$install_dir:"*) ;; *) echo "Add $install_dir to your PATH" ;; esac
echo "snunit needs scalino to build applications: https://github.com/lolgab/scalino"
