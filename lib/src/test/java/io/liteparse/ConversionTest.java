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
        // ImageMagick's PDF delegate (Ghostscript) is not reliably available on the
        // Windows/ARM64 runner (x64 emulation), so image->PDF is skipped there.
        assumeFalse(IS_WINDOWS && (System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)
                        .contains("aarch64") || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)
                        .contains("arm")),
                "image->PDF conversion is unreliable on Windows/ARM64");
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
