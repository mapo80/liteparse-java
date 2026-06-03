package io.liteparse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Locates and loads the native LiteParse JNI library together with its
 * companion files (PDFium, the C++ runtime libs on Linux, and the Tesseract
 * {@code eng.traineddata}).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Development / tests:</b> set the system property
 *       {@code -Dliteparse.native.dir=/path/to/dir}. The directory must already
 *       contain the JNI library and {@code libpdfium} side by side.</li>
 *   <li><b>Production:</b> the natives are packaged as classpath resources under
 *       {@code /io/liteparse/native/<classifier>/} (typically provided by a
 *       {@code liteparse-java-native-<classifier>} jar). They are extracted to a
 *       temporary directory and loaded from there. The PDFium library is found
 *       at runtime next to the JNI library via PDFium's own {@code self_dir()}
 *       probe, so no PATH/rpath tweaking is required on any platform.</li>
 * </ul>
 */
final class NativeLoader {

    /** System property pointing at a directory that already contains the natives. */
    static final String NATIVE_DIR_PROPERTY = "liteparse.native.dir";

    private static final String JNI_LIB = System.mapLibraryName("liteparse_jni");
    private static final Object LOCK = new Object();

    private static volatile boolean loaded = false;
    private static volatile Path nativeDir = null;

    private NativeLoader() {}

    /** The directory the natives were loaded from (used to locate bundled tessdata). */
    static Path nativeDir() {
        return nativeDir;
    }

    static void load() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            try {
                Path dir = resolveNativeDir();
                Path jniLib = dir.resolve(JNI_LIB);
                if (!Files.exists(jniLib)) {
                    throw new LiteParseException(
                            "Native library " + JNI_LIB + " not found in " + dir);
                }
                System.load(jniLib.toAbsolutePath().toString());
                nativeDir = dir;
                loaded = true;
            } catch (LiteParseException e) {
                throw e;
            } catch (Exception e) {
                throw new LiteParseException("Failed to load native LiteParse library", e);
            }
        }
    }

    private static Path resolveNativeDir() throws IOException {
        String override = System.getProperty(NATIVE_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return extractFromClasspath();
    }

    private static Path extractFromClasspath() throws IOException {
        String classifier = detectClassifier();
        String base = "/io/liteparse/native/" + classifier + "/";

        List<String> files = readManifest(base);
        if (files.isEmpty()) {
            throw new LiteParseException(
                    "No native binaries found for platform '" + classifier + "'. "
                            + "Add the dependency 'liteparse-java-native-" + classifier
                            + "' to your classpath, or set -D" + NATIVE_DIR_PROPERTY + ".");
        }

        Path tmp = Files.createTempDirectory("liteparse-native-");
        registerCleanup(tmp);

        for (String name : files) {
            String resource = base + name;
            try (InputStream in = NativeLoader.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new LiteParseException("Missing bundled native resource: " + resource);
                }
                Files.copy(in, tmp.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return tmp;
    }

    /** Reads the {@code manifest} file listing every file bundled for the platform. */
    private static List<String> readManifest(String base) throws IOException {
        List<String> files = new ArrayList<>();
        try (InputStream in = NativeLoader.class.getResourceAsStream(base + "manifest")) {
            if (in == null) {
                return files;
            }
            String content = new String(in.readAllBytes());
            for (String line : content.split("\\R")) {
                String name = line.trim();
                if (!name.isEmpty() && !name.equals("manifest")) {
                    files.add(name);
                }
            }
        }
        return files;
    }

    private static void registerCleanup(Path dir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir)));
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Best effort: a loaded library may still be locked (Windows).
                        }
                    });
        } catch (IOException ignored) {
            // Best effort.
        }
    }

    /** Maps {@code os.name}/{@code os.arch} to a platform classifier. */
    static String detectClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String osName;
        if (os.contains("win")) {
            osName = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "macos";
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            osName = "linux";
        } else {
            throw new LiteParseException("Unsupported operating system: " + os);
        }

        String archName;
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            archName = "aarch64";
        } else if (arch.equals("x86_64") || arch.equals("amd64")) {
            archName = "x86_64";
        } else {
            throw new LiteParseException("Unsupported CPU architecture: " + arch);
        }

        return osName + "-" + archName;
    }
}
