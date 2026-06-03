package io.liteparse.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for the CLI (no native library required). */
class MainTest {

    @Test
    void expandsPageSpecs() {
        assertArrayEquals(new int[] {1, 2, 3, 5, 10}, Main.parsePages("1-3,5,10"));
        assertArrayEquals(new int[] {3}, Main.parsePages("3"));
        assertArrayEquals(new int[] {1, 2, 3, 4}, Main.parsePages(" 1 , 2-4 "));
        assertArrayEquals(new int[] {2, 4, 6}, Main.parsePages("6,2,4"));
    }
}
