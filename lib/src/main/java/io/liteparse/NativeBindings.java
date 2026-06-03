package io.liteparse;

/**
 * Low-level JNI entry points. These map 1:1 to the
 * {@code Java_io_liteparse_NativeBindings_*} functions in the Rust crate.
 *
 * <p>This class is internal; application code should use {@link LiteParse}.
 * Complex values cross the boundary as JSON strings (parse results, config,
 * text items); screenshots are returned as {@link ScreenshotResult} objects.
 */
final class NativeBindings {

    static {
        NativeLoader.load();
    }

    private NativeBindings() {}

    /** Create a parser from a partial config JSON (camelCase). Returns an opaque handle. */
    static native long nativeNew(String configJson);

    /** Free a parser handle. */
    static native void nativeClose(long handle);

    /** Return the resolved config as a camelCase JSON string. */
    static native String nativeResolvedConfig(long handle);

    /** Parse from a file path. Returns a ParseResult JSON string. */
    static native String nativeParsePath(long handle, String path);

    /** Parse from raw bytes. Returns a ParseResult JSON string. */
    static native String nativeParseBytes(long handle, byte[] data);

    /** Render page screenshots. {@code pages} null/empty means all pages. */
    static native ScreenshotResult[] nativeScreenshot(long handle, String path, int[] pages);

    /** Merge text items matching {@code phrase}. Input/output are TextItem JSON arrays. */
    static native String nativeSearchItems(String itemsJson, String phrase, boolean caseSensitive);

    /**
     * Forces the native library to load (and surfaces any loading error eagerly).
     * No-op after the first call.
     */
    static void ensureLoaded() {
        // Touching this class runs the static initializer above.
    }
}
