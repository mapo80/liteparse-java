package io.liteparse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A parsed page with its projected text layout and individual text items.
 *
 * @param pageNum   1-based page number
 * @param width     page width in points
 * @param height    page height in points
 * @param text      layout-preserved text of the page
 * @param textItems individual text items with bounding boxes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedPage(
        int pageNum,
        double width,
        double height,
        String text,
        List<TextItem> textItems) {
}
