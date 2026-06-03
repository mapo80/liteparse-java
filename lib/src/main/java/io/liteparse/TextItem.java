package io.liteparse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single extracted text item with its position, size and font metadata.
 * Coordinates are in viewport space (top-left origin, 72 DPI).
 *
 * @param text       the text content
 * @param x          left position
 * @param y          top position
 * @param width      bounding-box width
 * @param height     bounding-box height
 * @param fontName   font name, if known
 * @param fontSize   font size, if known
 * @param fontWeight font weight (e.g. 400 normal, 700 bold), if known
 * @param fontFlags  PDF font descriptor flags (bit 0x40 = italic, 0x40000 = force-bold), if known
 * @param confidence OCR confidence (0.0–1.0); 1.0 for native PDF text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextItem(
        String text,
        double x,
        double y,
        double width,
        double height,
        String fontName,
        Double fontSize,
        Integer fontWeight,
        Integer fontFlags,
        Double confidence) {
}
