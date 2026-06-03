package io.liteparse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the JNI binding. Requires the native library to be
 * available, either via {@code -Dliteparse.native.dir=<dir>} (dev/CI) or a
 * {@code liteparse-java-<classifier>} jar on the classpath.
 */
class LiteParseTest {

    private static Path samplePdf;
    private static byte[] samplePdfBytes;

    @BeforeAll
    static void setup() throws Exception {
        try (InputStream in = LiteParseTest.class.getResourceAsStream("/sample.pdf")) {
            assertNotNull(in, "test resource /sample.pdf must exist");
            samplePdfBytes = in.readAllBytes();
        }
        samplePdf = Files.createTempFile("liteparse-test-", ".pdf");
        Files.write(samplePdf, samplePdfBytes);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (samplePdf != null) {
            Files.deleteIfExists(samplePdf);
        }
    }

    private static LiteParse newParser() {
        return new LiteParse(LiteParseConfig.builder().ocrEnabled(false).quiet(true).build());
    }

    @Test
    void parsesFromPath() {
        try (LiteParse parser = newParser()) {
            ParseResult result = parser.parse(samplePdf.toString());
            assertTrue(result.numPages() > 0, "expected at least one page");
            assertFalse(result.text().isBlank(), "expected non-empty text");
            ParsedPage page = result.page(1).orElseThrow();
            assertFalse(page.textItems().isEmpty(), "expected text items on page 1");
            assertTrue(page.width() > 0 && page.height() > 0);
        }
    }

    @Test
    void parsesFromBytes() {
        try (LiteParse parser = newParser()) {
            ParseResult result = parser.parse(samplePdfBytes);
            assertTrue(result.numPages() > 0);
            assertFalse(result.text().isBlank());
        }
    }

    @Test
    void rendersScreenshot() {
        try (LiteParse parser = newParser()) {
            List<ScreenshotResult> shots = parser.screenshot(samplePdf.toString(), 1);
            assertEquals(1, shots.size());
            ScreenshotResult shot = shots.get(0);
            assertEquals(1, shot.pageNum());
            assertTrue(shot.width() > 0 && shot.height() > 0);
            assertTrue(shot.image().length > 0, "expected non-empty PNG");
        }
    }

    @Test
    void exposesResolvedConfig() {
        try (LiteParse parser = newParser()) {
            LiteParseConfig config = parser.getConfig();
            assertEquals(Boolean.FALSE, config.ocrEnabled());
            assertNotNull(config.dpi());
            assertNotNull(config.maxPages());
        }
    }

    @Test
    void searchesItems() {
        TextItem a = new TextItem("Hello", 0, 0, 10, 5, "Arial", 12.0, null, null, 1.0);
        TextItem b = new TextItem("World", 11, 0, 10, 5, "Arial", 12.0, null, null, 1.0);
        List<TextItem> merged = LiteParse.searchItems(List.of(a, b), "Hello World", false);
        assertNotNull(merged);
    }
}
