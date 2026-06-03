# LiteParse for Java

[![CI](https://github.com/mapo80/liteparse-java/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mapo80/liteparse-java/actions/workflows/ci.yml)
[![Conversion tests](https://github.com/mapo80/liteparse-java/actions/workflows/conversion-tests.yml/badge.svg?branch=main)](https://github.com/mapo80/liteparse-java/actions/workflows/conversion-tests.yml)
[![Release](https://github.com/mapo80/liteparse-java/actions/workflows/release-java.yml/badge.svg)](https://github.com/mapo80/liteparse-java/actions/workflows/release-java.yml)
[![Latest release](https://img.shields.io/github/v/release/mapo80/liteparse-java?sort=semver)](https://github.com/mapo80/liteparse-java/releases/latest)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)

![Platforms](https://img.shields.io/badge/platforms-Linux%20%7C%20macOS%20%7C%20Windows%20%C2%B7%20x86__64%20%7C%20arm64-lightgrey.svg)

> CI builds and tests the JNI binding on **all six targets** (Linux/macOS/Windows × x86_64/arm64);
> the conversion suite verifies **PDF, image→OCR, and Office→PDF** conversion on each of them.

Java/JNI binding for [**LiteParse**](https://github.com/run-llama/liteparse) — a fast,
local, open-source document parser written in Rust. It extracts **layout-aware text with
bounding boxes** from PDFs, Office documents and images, renders **page screenshots**, and
performs **OCR** with a bundled Tesseract engine. Everything runs locally: no cloud, no
API keys, no Python.

This binding wraps the core `liteparse` Rust crate via JNI, exactly like the official
[Node.js](https://github.com/run-llama/liteparse/tree/main/packages/node) (napi-rs) and
[Python](https://github.com/run-llama/liteparse/tree/main/packages/python) (PyO3) packages.

> Version `2.0.5` of this binding tracks LiteParse core `2.0.5`.

## Features

- **Spatial text extraction** — text plus precise bounding boxes, powered by PDFium.
- **Multiple input formats** — PDF natively; DOCX/XLSX/PPTX/ODF (via LibreOffice) and images
  (via ImageMagick) are converted to PDF automatically when those tools are installed.
- **Bundled OCR** — Tesseract is compiled in and `eng.traineddata` is shipped in the native
  jar, so OCR works out of the box. Other languages and HTTP OCR servers are supported too.
- **Page screenshots** — high-quality PNG rendering at a configurable DPI.
- **Parse from a path or from in-memory bytes.**
- **No runtime native setup** — the correct platform binaries are picked from the classpath
  and PDFium is located automatically next to the JNI library on every OS.

## Supported platforms

| OS       | x86_64                  | aarch64 (arm64)         |
|----------|-------------------------|-------------------------|
| Linux    | ✅ `linux-x86_64`       | ✅ `linux-aarch64`      |
| macOS    | ✅ `macos-x86_64`       | ✅ `macos-aarch64`      |
| Windows  | ✅ `windows-x86_64`     | ✅ `windows-aarch64`    |

(Linux builds target glibc.)

## Installation

LiteParse for Java is distributed as **GitHub Release assets** (not on Maven Central). Each
release ships **self-contained bundle jars** that already contain the Java API, its dependency
(Jackson, shaded), and the native binaries (JNI library + PDFium + `eng.traineddata`). Drop one on
the classpath — no other dependencies, no native setup.

### All-platforms bundle (single jar — simplest)

`liteparse-java-bundle-<version>-all-platforms.jar` contains the native binaries for **all six
platforms** (Linux/macOS/Windows × x86_64/arm64). One download runs everywhere — the loader picks
the right native for the current OS/arch at runtime. Ideal when you don't want to match the platform
at build time (CI matrices, multi-OS deployments, "just works" distribution). ~57 MB.

```bash
java -cp "liteparse-java-bundle-<version>-all-platforms.jar:your-app.jar" com.example.App
```

### Per-platform bundle (smaller download)

If size matters, pick the single-platform `liteparse-java-bundle-<version>-<classifier>.jar` (~13 MB)
for your platform from the [latest release](https://github.com/mapo80/liteparse-java/releases/latest):

| Platform | classifier |
|----------|------------|
| Linux x86_64    | `linux-x86_64`    |
| Linux arm64     | `linux-aarch64`   |
| macOS x86_64    | `macos-x86_64`    |
| macOS arm64     | `macos-aarch64`   |
| Windows x86_64  | `windows-x86_64`  |
| Windows arm64   | `windows-aarch64` |

```bash
# example: Linux x86_64
curl -fsSL -o liteparse-java-bundle.jar \
  https://github.com/mapo80/liteparse-java/releases/latest/download/liteparse-java-bundle-2.0.5-linux-x86_64.jar

# run your app with the bundle on the classpath
java -cp "liteparse-java-bundle.jar:your-app.jar" com.example.App
```

Use it as a local file dependency:

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation(files("libs/liteparse-java-bundle-2.0.5-linux-x86_64.jar"))
}
```

```bash
# Maven: install the bundle into your local/internal repository
mvn install:install-file \
  -Dfile=liteparse-java-bundle-2.0.5-linux-x86_64.jar \
  -DgroupId=io.github.mapo80 -DartifactId=liteparse-java -Dversion=2.0.5 -Dpackaging=jar
```

> For multi-platform deployments, ship the bundle matching each target platform (or bundle
> several and let the loader pick the right one — the native loader selects the binaries for the
> current OS/arch from `io/liteparse/native/<classifier>/` on the classpath).

### Advanced: plain API jar

`liteparse-java-<version>.jar` contains only the Java classes (no natives, no Jackson). Use it
if you manage dependencies yourself: add `com.fasterxml.jackson.core:jackson-databind`, and make
the native library available either by putting a bundle jar's resources on the classpath or via
`-Dliteparse.native.dir=<dir>` (a directory containing the JNI library + `libpdfium`).

## Usage

```java
import io.liteparse.*;
import java.util.List;

try (LiteParse parser = new LiteParse(
        LiteParseConfig.builder()
            .ocrEnabled(true)        // OCR on (default)
            .ocrLanguage("eng")      // Tesseract language code
            .maxPages(50)
            .build())) {

    // Parse from a file path
    ParseResult result = parser.parse("document.pdf");
    System.out.println("Pages: " + result.numPages());
    System.out.println(result.text());

    // Inspect text items with bounding boxes
    for (ParsedPage page : result.pages()) {
        for (TextItem item : page.textItems()) {
            System.out.printf("%-20s @ (%.1f, %.1f) %.1fx%.1f%n",
                item.text(), item.x(), item.y(), item.width(), item.height());
        }
    }

    // Parse from in-memory bytes (e.g. a downloaded PDF)
    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("document.pdf"));
    ParseResult fromBytes = parser.parse(bytes);

    // Render page screenshots (PNG). Pass no page numbers to render all pages.
    List<ScreenshotResult> shots = parser.screenshot("document.pdf", 1, 2);
    for (ScreenshotResult shot : shots) {
        java.nio.file.Files.write(
            java.nio.file.Path.of("page-" + shot.pageNum() + ".png"), shot.image());
    }
}

// Merge text items matching a phrase (combines their bounding boxes)
List<TextItem> matches = LiteParse.searchItems(items, "Invoice Total", false);
```

### Configuration

`LiteParseConfig.builder()` exposes the same options as the CLI / other bindings:

| Option | Default | Description |
|--------|---------|-------------|
| `ocrEnabled` | `true` | Run OCR on text-sparse pages and embedded images. |
| `ocrLanguage` | `"eng"` | Tesseract language code (e.g. `eng`, `fra`, `deu`). |
| `ocrServerUrl` | — | Use an HTTP OCR server instead of the built-in Tesseract. |
| `tessdataPath` | bundled | Directory with `*.traineddata` files. Defaults to the bundled `eng.traineddata`. |
| `maxPages` | `1000` | Maximum number of pages to parse. |
| `targetPages` | all | Specific pages, e.g. `"1-5,10,15-20"`. |
| `dpi` | `150` | Rendering DPI for OCR and screenshots. |
| `outputFormat` | `JSON` | `JSON` or `TEXT`. |
| `preserveVerySmallText` | `false` | Keep very small text normally filtered out. |
| `password` | — | Password for encrypted documents. |
| `quiet` | `false` | Suppress progress output. |
| `numWorkers` | CPU-1 | Concurrent OCR workers. |

`parser.getConfig()` returns the fully-resolved configuration.

## Command-line tool

A small CLI (`io.liteparse.cli.Main`) ships inside the library jar for trying the library by
hand. It mirrors the upstream `lit` commands.

```
parse <file> [--json|--text] [--no-ocr] [--ocr-language LANG]
             [--max-pages N] [--target-pages 1-5,10] [--dpi DPI] [--password PW] [-o OUT]
screenshot <file> [-o DIR] [--pages 1,3,5-7] [--dpi DPI] [--password PW]
search <file> <phrase> [--case-sensitive] [--no-ocr]
```

Run it from a checkout via Gradle (point `-PnativeDir` at a directory containing the built
native library + `libpdfium`, e.g. `rust/target/<triple>/release`):

```bash
./gradlew :lib:runCli -PnativeDir="$PWD/rust/target/release" \
    -PcliArgs="parse document.pdf --text"
./gradlew :lib:runCli -PnativeDir=... -PcliArgs="screenshot document.pdf -o shots --pages 1,2"
./gradlew :lib:runCli -PnativeDir=... -PcliArgs="search document.pdf Invoice --no-ocr"
```

Or run it from the published jars on the classpath:

```bash
java -cp "liteparse-java.jar:liteparse-java-linux-x86_64.jar:jackson-*.jar" \
    io.liteparse.cli.Main parse document.pdf --json
```

`parse` prints plain text by default (use `--json` for the full structured result).

## OCR

The built-in **Tesseract** engine is compiled into the native library and `eng.traineddata`
is bundled, so OCR works with no extra setup.

- **Other languages:** download the `<lang>.traineddata` file(s) into a directory and set
  `tessdataPath(...)` (and `ocrLanguage("fra")`, etc.).
- **HTTP OCR servers** (EasyOCR, PaddleOCR, custom): set `ocrServerUrl(...)` to use them
  instead of the built-in Tesseract.

## Multi-format input

Non-PDF inputs are converted to PDF automatically when the relevant tool is available on the
system `PATH`:

- **Office documents** (`.docx`, `.xlsx`, `.pptx`, `.odt`, `.rtf`, …) — requires **LibreOffice**.
- **Images** (`.png`, `.jpg`, `.tiff`, `.webp`, …) — requires **ImageMagick**.

```bash
# macOS
brew install --cask libreoffice && brew install imagemagick
# Debian/Ubuntu
sudo apt-get install libreoffice imagemagick
# Windows
choco install libreoffice-fresh imagemagick.app
```

## How the native binaries are built (and what is reused)

To keep build effort minimal, this project **reuses every prebuilt binary that exists** and
only compiles what has no usable prebuilt for the JVM:

| Component | Source |
|-----------|--------|
| **PDFium** | **Downloaded prebuilt** from [`run-llama/pdfium-binaries`](https://github.com/run-llama/pdfium-binaries) (all platforms, Windows included). Never compiled here. |
| **Tesseract `eng.traineddata`** | **Downloaded prebuilt** from [`tessdata_fast`](https://github.com/tesseract-ocr/tessdata_fast). |
| **LiteParse core** | Pulled from crates.io (`liteparse = 2.0.5`); no fork of the upstream monorepo. |
| **JNI library + Tesseract engine** | **Compiled** in CI on native runners for each platform — this is the only part with no reusable prebuilt (no JNI/C-ABI native is published upstream). |

At runtime, PDFium is located **next to the JNI library** via PDFium's own `self_dir()` probe,
so no `PATH`, rpath or environment tweaking is needed on any OS — including Windows.

## Building from source

Prerequisites: **Rust 1.95+**, **JDK 17+**, **CMake**, and (Windows) **Ninja**. For Tesseract:
`libtesseract-dev libleptonica-dev` (Linux) or `brew install tesseract leptonica` (macOS).

```bash
# 1) Build the JNI native library (downloads prebuilt PDFium automatically)
cd rust
cargo build --release            # add --no-default-features for a build without bundled OCR
cd ..

# 2) Stage the native files for your platform (JNI lib + PDFium + tessdata [+ C++ libs on Linux])
#    classifier ∈ {linux-x86_64, linux-aarch64, macos-x86_64, macos-aarch64, windows-*}
bash scripts/stage-natives.sh aarch64-apple-darwin macos-aarch64 native-staging/macos-aarch64
#   (Windows: pwsh scripts/stage-natives.ps1 -Target ... -Classifier ... -OutDir ...)

# 3) Build & test the Java library against the staged natives
./gradlew :lib:build -PnativeDir="$PWD/native-staging/macos-aarch64"
```

During development you can also point the loader at any directory that already contains the
native library and `libpdfium` with `-Dliteparse.native.dir=/path/to/dir`.

## Releasing

`.github/workflows/release-java.yml` (manual `workflow_dispatch`) builds the native library on
all six platforms, assembles the plain API jar + sources + javadoc + the six self-contained
bundle jars, and attaches them to a **GitHub Release**. No Maven Central / Sonatype account, GPG
key, or secrets are required. Run it with `dry-run: true` first to validate the artifacts, then
with `dry-run: false` to tag and publish the release. See **[RELEASING.md](RELEASING.md)** for
the step-by-step.

`.github/workflows/ci.yml` builds and tests on every platform for pushes and pull requests.

## License

[Apache-2.0](LICENSE). Built on top of [LiteParse](https://github.com/run-llama/liteparse),
[PDFium](https://pdfium.googlesource.com/pdfium/) and
[Tesseract](https://github.com/tesseract-ocr/tesseract).
