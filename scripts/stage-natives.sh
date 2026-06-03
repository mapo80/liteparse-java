#!/usr/bin/env bash
#
# Collect the freshly built native files for one platform into a staging
# directory that the Gradle `nativeJar-<classifier>` task packs into a jar.
#
# Usage: stage-natives.sh <rust-target-triple> <classifier> <out-dir> [with-tessdata=1]
#
# Bundles, side by side (so PDFium's self_dir() probe finds everything):
#   - the JNI library (libliteparse_jni.{so,dylib})
#   - libpdfium (downloaded prebuilt by liteparse-pdfium-sys; never compiled here)
#   - on Linux: the non-system C++ runtime libraries the JNI lib links
#   - eng.traineddata for the bundled Tesseract OCR (prebuilt, downloaded)
#
# Use scripts/stage-natives.ps1 on Windows.
set -euo pipefail

TARGET="${1:?usage: stage-natives.sh <target> <classifier> <out-dir> [with-tessdata]}"
CLASSIFIER="${2:?missing classifier}"
OUT="${3:?missing out dir}"
WITH_TESSDATA="${4:-1}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_DIR="$REPO/rust/target/$TARGET/release"

mkdir -p "$OUT"

case "$(uname -s)" in
    Darwin*) JNI="libliteparse_jni.dylib"; PDFIUM="libpdfium.dylib" ;;
    Linux*)  JNI="libliteparse_jni.so";    PDFIUM="libpdfium.so" ;;
    *) echo "Unsupported OS for this script; use stage-natives.ps1 on Windows" >&2; exit 1 ;;
esac

# 1) JNI library
if [ ! -f "$RELEASE_DIR/$JNI" ]; then
    echo "JNI library not found at $RELEASE_DIR/$JNI (did 'cargo build --release' run?)" >&2
    exit 1
fi
cp "$RELEASE_DIR/$JNI" "$OUT/$JNI"

# 2) PDFium — prefer the copy build.rs places in deps/, then env, then the cache.
find_pdfium() {
    if [ -f "$RELEASE_DIR/deps/$PDFIUM" ]; then echo "$RELEASE_DIR/deps/$PDFIUM"; return; fi
    if [ -n "${PDFIUM_LIB_PATH:-}" ] && [ -f "${PDFIUM_LIB_PATH}/$PDFIUM" ]; then
        echo "${PDFIUM_LIB_PATH}/$PDFIUM"; return
    fi
    local base
    case "$(uname -s)" in
        Darwin*) base="$HOME/Library/Caches/pdfium-rs" ;;
        *)       base="${XDG_CACHE_HOME:-$HOME/.cache}/pdfium-rs" ;;
    esac
    [ -d "$base" ] && find "$base" -name "$PDFIUM" -type f 2>/dev/null | head -1
}
PDFIUM_SRC="$(find_pdfium)"
if [ -z "$PDFIUM_SRC" ]; then
    echo "Could not locate $PDFIUM. Set PDFIUM_LIB_PATH." >&2
    exit 1
fi
cp "$PDFIUM_SRC" "$OUT/$PDFIUM"
if [ "$(uname -s)" = "Darwin" ]; then
    # Ensure @rpath install name so it resolves next to the JNI lib.
    install_name_tool -id "@rpath/libpdfium.dylib" "$OUT/$PDFIUM" 2>/dev/null || true
fi

# 3) Linux: bundle the non-system C++ runtime libraries the JNI lib depends on.
if [ "$(uname -s)" = "Linux" ]; then
    for lib in libstdc++.so.6 libc++.so.1 libc++abi.so.1 libunwind.so.1 libgcc_s.so.1; do
        src="$(ldd "$OUT/$JNI" 2>/dev/null | awk -v l="$lib" '$1==l {print $3}')"
        if [ -n "$src" ] && [ -f "$src" ]; then
            cp -L "$src" "$OUT/$lib"
            echo "bundled $lib ($src)"
        fi
    done
fi

# 4) Tesseract language data (prebuilt, downloaded — never generated here).
if [ "$WITH_TESSDATA" = "1" ]; then
    bash "$SCRIPT_DIR/download-tessdata.sh" "$OUT" eng
fi

echo "Staged native files for $CLASSIFIER in $OUT:"
ls -la "$OUT"
