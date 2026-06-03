package io.liteparse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;

/**
 * Result of parsing a document.
 *
 * @param pages parsed pages, in order
 * @param text  full document text, concatenated from all pages
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParseResult(List<ParsedPage> pages, String text) {

    /** Number of parsed pages. */
    public int numPages() {
        return pages == null ? 0 : pages.size();
    }

    /** Find a page by its 1-based page number. */
    public Optional<ParsedPage> page(int pageNum) {
        if (pages == null) {
            return Optional.empty();
        }
        return pages.stream().filter(p -> p.pageNum() == pageNum).findFirst();
    }
}
