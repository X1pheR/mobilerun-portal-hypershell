#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
rm -f SOURCE-MANIFEST.sha256
find . -type f \
  ! -path './.git/*' \
  ! -path './.gradle/*' \
  ! -path './build/*' \
  ! -path './app/build/*' \
  ! -path './.idea/*' \
  ! -path '*/__pycache__/*' \
  ! -name '*.pyc' \
  ! -path './SOURCE-MANIFEST.sha256' \
  -print0 \
  | sort -z \
  | xargs -0 sha256sum > SOURCE-MANIFEST.sha256
printf 'manifest_files=%s\n' "$(wc -l < SOURCE-MANIFEST.sha256)"
