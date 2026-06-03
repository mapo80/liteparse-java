#!/usr/bin/env bash
#
# Download prebuilt Tesseract language data (never generated here) into a
# directory. Used by stage-natives.sh to bundle OCR data next to the native lib.
#
# Usage: download-tessdata.sh <out-dir> [lang ...]   (default lang: eng)
set -euo pipefail

OUT="${1:?usage: download-tessdata.sh <out-dir> [lang ...]}"
shift || true
LANGS=("$@")
[ ${#LANGS[@]} -eq 0 ] && LANGS=("eng")

# tessdata_fast = good speed/accuracy trade-off and small files.
BASE="https://github.com/tesseract-ocr/tessdata_fast/raw/main"

mkdir -p "$OUT"
for lang in "${LANGS[@]}"; do
    echo "Downloading ${lang}.traineddata"
    curl -fsSL "${BASE}/${lang}.traineddata" -o "${OUT}/${lang}.traineddata"
done
