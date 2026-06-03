package io.liteparse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a {@link LiteParse} instance.
 *
 * <p>All fields are nullable: a {@code null} field means "use the native default".
 * Build instances with {@link #builder()}. The same type is also used to expose
 * the fully-resolved configuration via {@link LiteParse#getConfig()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LiteParseConfig {

    @JsonProperty("ocrLanguage") private final String ocrLanguage;
    @JsonProperty("ocrEnabled") private final Boolean ocrEnabled;
    @JsonProperty("ocrServerUrl") private final String ocrServerUrl;
    @JsonProperty("tessdataPath") private final String tessdataPath;
    @JsonProperty("maxPages") private final Integer maxPages;
    @JsonProperty("targetPages") private final String targetPages;
    @JsonProperty("dpi") private final Double dpi;
    @JsonProperty("outputFormat") private final OutputFormat outputFormat;
    @JsonProperty("preserveVerySmallText") private final Boolean preserveVerySmallText;
    @JsonProperty("password") private final String password;
    @JsonProperty("quiet") private final Boolean quiet;
    @JsonProperty("numWorkers") private final Integer numWorkers;

    LiteParseConfig(
            @JsonProperty("ocrLanguage") String ocrLanguage,
            @JsonProperty("ocrEnabled") Boolean ocrEnabled,
            @JsonProperty("ocrServerUrl") String ocrServerUrl,
            @JsonProperty("tessdataPath") String tessdataPath,
            @JsonProperty("maxPages") Integer maxPages,
            @JsonProperty("targetPages") String targetPages,
            @JsonProperty("dpi") Double dpi,
            @JsonProperty("outputFormat") OutputFormat outputFormat,
            @JsonProperty("preserveVerySmallText") Boolean preserveVerySmallText,
            @JsonProperty("password") String password,
            @JsonProperty("quiet") Boolean quiet,
            @JsonProperty("numWorkers") Integer numWorkers) {
        this.ocrLanguage = ocrLanguage;
        this.ocrEnabled = ocrEnabled;
        this.ocrServerUrl = ocrServerUrl;
        this.tessdataPath = tessdataPath;
        this.maxPages = maxPages;
        this.targetPages = targetPages;
        this.dpi = dpi;
        this.outputFormat = outputFormat;
        this.preserveVerySmallText = preserveVerySmallText;
        this.password = password;
        this.quiet = quiet;
        this.numWorkers = numWorkers;
    }

    /** A configuration that uses all native defaults. */
    public static LiteParseConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String ocrLanguage() { return ocrLanguage; }
    public Boolean ocrEnabled() { return ocrEnabled; }
    public String ocrServerUrl() { return ocrServerUrl; }
    public String tessdataPath() { return tessdataPath; }
    public Integer maxPages() { return maxPages; }
    public String targetPages() { return targetPages; }
    public Double dpi() { return dpi; }
    public OutputFormat outputFormat() { return outputFormat; }
    public Boolean preserveVerySmallText() { return preserveVerySmallText; }
    public String password() { return password; }
    public Boolean quiet() { return quiet; }
    public Integer numWorkers() { return numWorkers; }

    /** Returns a copy with {@code tessdataPath} set if it was {@code null}. */
    LiteParseConfig withTessdataPathIfAbsent(String path) {
        if (this.tessdataPath != null || path == null) {
            return this;
        }
        return new LiteParseConfig(ocrLanguage, ocrEnabled, ocrServerUrl, path, maxPages,
                targetPages, dpi, outputFormat, preserveVerySmallText, password, quiet, numWorkers);
    }

    @Override
    public String toString() {
        return "LiteParseConfig{ocrEnabled=" + ocrEnabled
                + ", ocrLanguage=" + ocrLanguage
                + ", dpi=" + dpi
                + ", maxPages=" + maxPages
                + ", outputFormat=" + outputFormat + "}";
    }

    /** Fluent builder for {@link LiteParseConfig}. */
    public static final class Builder {
        private String ocrLanguage;
        private Boolean ocrEnabled;
        private String ocrServerUrl;
        private String tessdataPath;
        private Integer maxPages;
        private String targetPages;
        private Double dpi;
        private OutputFormat outputFormat;
        private Boolean preserveVerySmallText;
        private String password;
        private Boolean quiet;
        private Integer numWorkers;

        /** OCR language code, Tesseract format (e.g. "eng", "fra", "deu"). */
        public Builder ocrLanguage(String v) { this.ocrLanguage = v; return this; }
        /** Enable or disable OCR (default: enabled). */
        public Builder ocrEnabled(boolean v) { this.ocrEnabled = v; return this; }
        /** HTTP OCR server URL; if set, used instead of the built-in Tesseract. */
        public Builder ocrServerUrl(String v) { this.ocrServerUrl = v; return this; }
        /** Path to a Tesseract {@code tessdata} directory. */
        public Builder tessdataPath(String v) { this.tessdataPath = v; return this; }
        /** Maximum number of pages to parse. */
        public Builder maxPages(int v) { this.maxPages = v; return this; }
        /** Specific pages to parse, e.g. "1-5,10,15-20". */
        public Builder targetPages(String v) { this.targetPages = v; return this; }
        /** Rendering DPI for OCR and screenshots. */
        public Builder dpi(double v) { this.dpi = v; return this; }
        /** Output format. */
        public Builder outputFormat(OutputFormat v) { this.outputFormat = v; return this; }
        /** Keep very small text that would normally be filtered out. */
        public Builder preserveVerySmallText(boolean v) { this.preserveVerySmallText = v; return this; }
        /** Password for encrypted documents. */
        public Builder password(String v) { this.password = v; return this; }
        /** Suppress progress output. */
        public Builder quiet(boolean v) { this.quiet = v; return this; }
        /** Number of concurrent OCR workers (default: CPU cores - 1). */
        public Builder numWorkers(int v) { this.numWorkers = v; return this; }

        public LiteParseConfig build() {
            return new LiteParseConfig(ocrLanguage, ocrEnabled, ocrServerUrl, tessdataPath,
                    maxPages, targetPages, dpi, outputFormat, preserveVerySmallText, password,
                    quiet, numWorkers);
        }
    }
}
