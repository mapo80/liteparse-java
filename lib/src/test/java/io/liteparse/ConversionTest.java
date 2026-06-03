package io.liteparse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end conversion tests: verify that LiteParse correctly turns the
 * supported input formats into parsed text on the current platform.
 *
 * <ul>
 *   <li>PDF — parsed natively (no external tool).</li>
 *   <li>Image — converted to PDF via <b>ImageMagick</b>, then OCR'd via the
 *       bundled <b>Tesseract</b>.</li>
 *   <li>Office document — converted to PDF via <b>LibreOffice</b>, then parsed.</li>
 * </ul>
 *
 * The image/Office cases are skipped (not failed) when the required external
 * tool is not on {@code PATH} — the same lookup LiteParse itself uses — so this
 * class reports, per environment, which conversions are actually exercised. The
 * {@code conversion-tests.yml} workflow installs the tools so all three run on
 * every platform.
 */
@Tag("conversion")
class ConversionTest {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final boolean IS_ARM = isArm();

    /**
     * Image -> PDF on the Windows/ARM64 runner goes through an x64-emulated
     * ImageMagick, which historically had an unreliable PDF delegate. The
     * dedicated {@code win-arm64.yml} pipeline installs a working tool-chain and
     * sets this flag to prove the conversion actually runs; once that pipeline is
     * green the flag is the only thing standing between Win/ARM64 and the rest of
     * the matrix.
     */
    private static final boolean FORCE_IMAGE_CONVERSION =
            Boolean.getBoolean("liteparse.forceImageConversion");

    private static boolean isArm() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm");
    }

    @Test
    void parsesPdfNatively() throws Exception {
        Path pdf = extract("sample.pdf");
        try (LiteParse parser = config(false)) {
            ParseResult result = parser.parse(pdf.toString());
            assertTrue(result.numPages() > 0, "expected at least one page");
            assertTrue(result.text().toLowerCase(Locale.ROOT).contains("sample"),
                    "expected 'sample' in extracted PDF text");
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void convertsImageAndRunsOcr() throws Exception {
        // ImageMagick's PDF delegate has historically been unreliable on the
        // Windows/ARM64 runner (x64 emulation), so image->PDF is skipped there
        // unless the dedicated win-arm64.yml pipeline forces it on via
        // -PforceImageConversion (which also installs a working tool-chain).
        assumeFalse(IS_WINDOWS && IS_ARM && !FORCE_IMAGE_CONVERSION,
                "image->PDF conversion is unreliable on Windows/ARM64 "
                        + "(set -PforceImageConversion=true to exercise it)");
        assumeTrue(onPath("magick", "convert"),
                "ImageMagick not on PATH — skipping image conversion test");
        Path image = extract("receipt.png");
        try (LiteParse parser = config(true)) {
            ParseResult result = parser.parse(image.toString());
            assertTrue(result.numPages() > 0, "expected a page from image->PDF conversion");
            assertTrue(result.text().strip().length() > 15,
                    "expected non-trivial OCR text, got: [" + result.text().strip() + "]");
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void convertsOfficeDocument() throws Exception {
        assumeTrue(onPath("soffice", "libreoffice"),
                "LibreOffice not on PATH — skipping Office conversion test");
        Path doc = extract("sample.doc");
        try (LiteParse parser = config(false)) {
            ParseResult result = parser.parse(doc.toString());
            assertTrue(result.numPages() > 0, "expected a page from Office->PDF conversion");
            assertFalse(result.text().isBlank(), "expected text from the converted Office document");
        }
    }

    // ----------------------------------------------------------------- helpers

    private static LiteParse config(boolean ocr) {
        return new LiteParse(LiteParseConfig.builder().ocrEnabled(ocr).quiet(true).build());
    }

    private static Path extract(String resource) throws Exception {
        Path out = Files.createTempFile("liteparse-conv-", "-" + resource);
        try (InputStream in = ConversionTest.class.getResourceAsStream("/" + resource)) {
            assertNotNull(in, "missing test resource /" + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
        out.toFile().deleteOnExit();
        return out;
    }

    /** Mirrors LiteParse's own executable lookup: search each PATH entry. */
    private static boolean onPath(String... executables) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String exe : executables) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.isBlank()) {
                    continue;
                }
                Path base = Path.of(dir, exe);
                if (Files.isRegularFile(base)) {
                    return true;
                }
                if (IS_WINDOWS) {
                    for (String ext : new String[] {".exe", ".com", ".bat"}) {
                        if (Files.isRegularFile(Path.of(dir, exe + ext))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
