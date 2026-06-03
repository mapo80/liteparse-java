package io.liteparse;

/**
 * A rendered page screenshot: PNG-encoded image bytes plus dimensions.
 *
 * <p>Instances are constructed directly by the native layer; the constructor
 * signature {@code (int, int, int, byte[])} must stay in sync with
 * {@code Java_io_liteparse_NativeBindings_nativeScreenshot} in the Rust crate.
 */
public final class ScreenshotResult {

    private final int pageNum;
    private final int width;
    private final int height;
    private final byte[] image;

    /** Invoked from native code. Do not change the signature without updating the Rust side. */
    public ScreenshotResult(int pageNum, int width, int height, byte[] image) {
        this.pageNum = pageNum;
        this.width = width;
        this.height = height;
        this.image = image;
    }

    /** 1-based page number. */
    public int pageNum() {
        return pageNum;
    }

    /** Image width in pixels. */
    public int width() {
        return width;
    }

    /** Image height in pixels. */
    public int height() {
        return height;
    }

    /** PNG-encoded image bytes. */
    public byte[] image() {
        return image;
    }

    @Override
    public String toString() {
        return "ScreenshotResult{pageNum=" + pageNum
                + ", width=" + width
                + ", height=" + height
                + ", image=" + (image == null ? 0 : image.length) + " bytes}";
    }
}
