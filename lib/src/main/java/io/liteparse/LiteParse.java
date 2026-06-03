package io.liteparse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fast, local document parser with spatial text extraction, OCR and screenshots.
 *
 * <p>Backed by the Rust <a href="https://github.com/run-llama/liteparse">LiteParse</a>
 * core via JNI. Parses PDFs, Office documents and images into layout-aware text
 * with bounding boxes. Office/image inputs are converted to PDF automatically
 * when LibreOffice / ImageMagick are available on the system.
 *
 * <p>Instances hold a native handle and are {@link AutoCloseable}; use
 * try-with-resources. A {@link Cleaner} also frees the handle if {@link #close()}
 * is not called.
 *
 * <pre>{@code
 * try (LiteParse parser = new LiteParse(
 *         LiteParseConfig.builder().ocrEnabled(false).build())) {
 *     ParseResult result = parser.parse("document.pdf");
 *     System.out.println(result.text());
 * }
 * }</pre>
 *
 * <p>This class is not thread-safe; use one instance per thread or synchronize.
 */
public final class LiteParse implements AutoCloseable {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private static final Cleaner CLEANER = Cleaner.create();
    private static final String BUNDLED_TESSDATA = "eng.traineddata";

    private final long handle;
    private final LiteParseConfig config;
    private final Cleaner.Cleanable cleanable;
    private volatile boolean closed = false;

    /** Create a parser with default configuration. */
    public LiteParse() {
        this(LiteParseConfig.defaults());
    }

    /** Create a parser with the given configuration. */
    public LiteParse(LiteParseConfig userConfig) {
        NativeBindings.ensureLoaded();
        LiteParseConfig effective = applyBundledTessdata(userConfig);
        this.handle = NativeBindings.nativeNew(toJson(effective));
        if (this.handle == 0L) {
            throw new LiteParseException("Failed to create native parser");
        }
        this.cleanable = CLEANER.register(this, new Closer(handle));
        this.config = parse(NativeBindings.nativeResolvedConfig(handle), LiteParseConfig.class);
    }

    /** Parse a document from a file path. */
    public ParseResult parse(String path) {
        ensureOpen();
        return parse(NativeBindings.nativeParsePath(handle, path), ParseResult.class);
    }

    /** Parse a document from raw in-memory bytes (e.g. a downloaded PDF). */
    public ParseResult parse(byte[] data) {
        ensureOpen();
        return parse(NativeBindings.nativeParseBytes(handle, data), ParseResult.class);
    }

    /**
     * Render page screenshots as PNG images.
     *
     * @param path  document path
     * @param pages 1-based page numbers; pass none to render all pages
     */
    public List<ScreenshotResult> screenshot(String path, int... pages) {
        ensureOpen();
        ScreenshotResult[] results = NativeBindings.nativeScreenshot(handle, path, pages);
        return List.of(results);
    }

    /** The configuration resolved by the native layer (all defaults filled in). */
    public LiteParseConfig getConfig() {
        return config;
    }

    /**
     * Search text items for phrase matches, returning merged items with combined
     * bounding boxes.
     */
    public static List<TextItem> searchItems(List<TextItem> items, String phrase,
                                             boolean caseSensitive) {
        NativeBindings.ensureLoaded();
        String itemsJson;
        try {
            itemsJson = MAPPER.writeValueAsString(items);
        } catch (Exception e) {
            throw new LiteParseException("Failed to serialize text items", e);
        }
        String out = NativeBindings.nativeSearchItems(itemsJson, phrase, caseSensitive);
        return parse(out, new TypeReference<List<TextItem>>() {});
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cleanable.clean();
        }
    }

    // ---------------------------------------------------------------------

    private void ensureOpen() {
        if (closed) {
            throw new LiteParseException("LiteParse instance is closed");
        }
    }

    /**
     * If the user did not set a tessdata path, default it to the bundled
     * {@code eng.traineddata} next to the native library so OCR works
     * out of the box (mirrors the "Tesseract bundled" behaviour of the
     * Node/Python packages).
     */
    private static LiteParseConfig applyBundledTessdata(LiteParseConfig cfg) {
        Path dir = NativeLoader.nativeDir();
        if (dir != null && Files.exists(dir.resolve(BUNDLED_TESSDATA))) {
            return cfg.withTessdataPathIfAbsent(dir.toAbsolutePath().toString());
        }
        return cfg;
    }

    private static String toJson(LiteParseConfig cfg) {
        try {
            return MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new LiteParseException("Failed to serialize config", e);
        }
    }

    private static <T> T parse(String json, Class<T> type) {
        if (json == null) {
            throw new LiteParseException("Native call returned no data");
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new LiteParseException("Failed to parse native response", e);
        }
    }

    private static <T> T parse(String json, TypeReference<T> type) {
        if (json == null) {
            throw new LiteParseException("Native call returned no data");
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new LiteParseException("Failed to parse native response", e);
        }
    }

    /** Holds only the native handle so the Cleaner action does not pin the LiteParse. */
    private record Closer(long handle) implements Runnable {
        @Override
        public void run() {
            NativeBindings.nativeClose(handle);
        }
    }
}
