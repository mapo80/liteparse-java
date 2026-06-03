package io.liteparse.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.liteparse.LiteParse;
import io.liteparse.LiteParseConfig;
import io.liteparse.OutputFormat;
import io.liteparse.ParseResult;
import io.liteparse.ParsedPage;
import io.liteparse.ScreenshotResult;
import io.liteparse.TextItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Small command-line tool to exercise the LiteParse library by hand.
 *
 * <pre>
 *   lit-java parse &lt;file&gt; [--json|--text] [--no-ocr] [--ocr-language eng]
 *                         [--max-pages N] [--target-pages 1-5,10] [--dpi 150]
 *                         [--password PW] [-o OUT]
 *   lit-java screenshot &lt;file&gt; [-o DIR] [--pages 1,3,5-7] [--dpi 150] [--password PW]
 *   lit-java search &lt;file&gt; &lt;phrase&gt; [--case-sensitive] [--no-ocr]
 * </pre>
 *
 * Run via Gradle: {@code ./gradlew :lib:runCli -PnativeDir=<dir> -PcliArgs="parse f.pdf --text"}
 * or on the classpath: {@code java -cp <jars> io.liteparse.cli.Main parse f.pdf}.
 */
public final class Main {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        String command = args[0];
        Args rest = new Args(args, 1);
        try {
            switch (command) {
                case "parse" -> parse(rest);
                case "screenshot" -> screenshot(rest);
                case "search" -> search(rest);
                case "-h", "--help", "help" -> usage();
                default -> {
                    System.err.println("Unknown command: " + command);
                    usage();
                    System.exit(2);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    // ----------------------------------------------------------------- parse
    private static void parse(Args a) throws Exception {
        String file = a.positional(0, "file");
        boolean json = a.flag("--json");
        boolean text = a.flag("--text");
        LiteParseConfig.Builder cfg = LiteParseConfig.builder().quiet(true);
        if (a.flag("--no-ocr")) {
            cfg.ocrEnabled(false);
        }
        a.value("--ocr-language").ifPresent(cfg::ocrLanguage);
        a.value("--max-pages").ifPresent(v -> cfg.maxPages(Integer.parseInt(v)));
        a.value("--target-pages").ifPresent(cfg::targetPages);
        a.value("--dpi").ifPresent(v -> cfg.dpi(Double.parseDouble(v)));
        a.value("--password").ifPresent(cfg::password);

        String out;
        try (LiteParse parser = new LiteParse(cfg.build())) {
            ParseResult result = parser.parse(file);
            // Default to text unless --json is given.
            if (json && !text) {
                out = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            } else {
                out = result.text();
            }
        }
        a.value("-o").or(() -> a.value("--output")).ifPresentOrElse(
                p -> writeString(Path.of(p), finalNl(out)),
                () -> System.out.println(out));
    }

    // ------------------------------------------------------------ screenshot
    private static void screenshot(Args a) throws Exception {
        String file = a.positional(0, "file");
        Path outDir = Path.of(a.value("-o").or(() -> a.value("--output")).orElse("screenshots"));
        int[] pages = a.value("--pages").map(Main::parsePages).orElse(new int[0]);
        LiteParseConfig.Builder cfg = LiteParseConfig.builder().quiet(true);
        a.value("--dpi").ifPresent(v -> cfg.dpi(Double.parseDouble(v)));
        a.value("--password").ifPresent(cfg::password);

        Files.createDirectories(outDir);
        try (LiteParse parser = new LiteParse(cfg.build())) {
            List<ScreenshotResult> shots = parser.screenshot(file, pages);
            for (ScreenshotResult shot : shots) {
                Path png = outDir.resolve("page-" + shot.pageNum() + ".png");
                Files.write(png, shot.image());
                System.out.printf("wrote %s (%dx%d, %d bytes)%n",
                        png, shot.width(), shot.height(), shot.image().length);
            }
            System.out.println(shots.size() + " screenshot(s) written to " + outDir);
        }
    }

    // ---------------------------------------------------------------- search
    private static void search(Args a) {
        String file = a.positional(0, "file");
        String phrase = a.positional(1, "phrase");
        boolean caseSensitive = a.flag("--case-sensitive");
        LiteParseConfig.Builder cfg = LiteParseConfig.builder().quiet(true);
        if (a.flag("--no-ocr")) {
            cfg.ocrEnabled(false);
        }
        List<TextItem> allItems = new ArrayList<>();
        try (LiteParse parser = new LiteParse(cfg.build())) {
            ParseResult result = parser.parse(file);
            for (ParsedPage page : result.pages()) {
                allItems.addAll(page.textItems());
            }
        }
        List<TextItem> matches = LiteParse.searchItems(allItems, phrase, caseSensitive);
        if (matches.isEmpty()) {
            System.out.println("No matches for \"" + phrase + "\"");
            return;
        }
        System.out.println(matches.size() + " match(es) for \"" + phrase + "\":");
        for (TextItem m : matches) {
            System.out.printf("  %-40s @ (%.1f, %.1f) %.1fx%.1f%n",
                    truncate(m.text(), 40), m.x(), m.y(), m.width(), m.height());
        }
    }

    // ----------------------------------------------------------------- utils
    /** Expands a page spec like "1,3,5-7" into a sorted, de-duplicated int array. */
    static int[] parsePages(String spec) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            int dash = part.indexOf('-');
            if (dash > 0) {
                int start = Integer.parseInt(part.substring(0, dash).trim());
                int end = Integer.parseInt(part.substring(dash + 1).trim());
                for (int p = start; p <= end; p++) {
                    pages.add(p);
                }
            } else {
                pages.add(Integer.parseInt(part));
            }
        }
        return pages.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String finalNl(String s) {
        return s.endsWith("\n") ? s : s + "\n";
    }

    private static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content);
            System.out.println("wrote " + path);
        } catch (Exception e) {
            throw new RuntimeException("failed to write " + path + ": " + e.getMessage(), e);
        }
    }

    private static void usage() {
        System.out.println("""
            LiteParse CLI

            Usage:
              parse <file> [--json|--text] [--no-ocr] [--ocr-language LANG]
                           [--max-pages N] [--target-pages 1-5,10] [--dpi DPI]
                           [--password PW] [-o OUT]
              screenshot <file> [-o DIR] [--pages 1,3,5-7] [--dpi DPI] [--password PW]
              search <file> <phrase> [--case-sensitive] [--no-ocr]

            Output defaults to plain text for `parse`; use --json for structured output.
            """);
    }

    /** Minimal arg parser: positionals, --flag, and --key value / --key=value. */
    private static final class Args {
        private final List<String> tokens;

        Args(String[] args, int from) {
            this.tokens = new ArrayList<>(List.of(args).subList(from, args.length));
        }

        boolean flag(String name) {
            return tokens.remove(name);
        }

        java.util.Optional<String> value(String name) {
            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (t.equals(name)) {
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("missing value for " + name);
                    }
                    String v = tokens.get(i + 1);
                    tokens.remove(i + 1);
                    tokens.remove(i);
                    return java.util.Optional.of(v);
                }
                if (t.startsWith(name + "=")) {
                    tokens.remove(i);
                    return java.util.Optional.of(t.substring(name.length() + 1));
                }
            }
            return java.util.Optional.empty();
        }

        String positional(int index, String label) {
            int seen = 0;
            for (String t : tokens) {
                if (t.startsWith("-")) {
                    continue;
                }
                if (seen == index) {
                    return t;
                }
                seen++;
            }
            throw new IllegalArgumentException("missing argument: " + label);
        }
    }
}
