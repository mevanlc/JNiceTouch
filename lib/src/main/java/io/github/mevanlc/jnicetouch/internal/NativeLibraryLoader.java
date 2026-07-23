package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.JNiceTouchException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NativeLibraryLoader {
    static final String LIBRARY_PATH_PROPERTY = "jnicetouch.native.library";
    private static final String RESOURCE_PATH =
            "/META-INF/native/macos-universal/libjnicetouch.dylib";

    private static boolean loaded;

    private NativeLibraryLoader() {}

    static synchronized void load() {
        if (loaded) {
            return;
        }
        if (!System.getProperty("os.name").equals("Mac OS X")) {
            throw new JNiceTouchException("The JNiceTouch native library supports only macOS");
        }

        String configuredPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        try {
            Path library = configuredPath == null || configuredPath.isBlank()
                    ? extractBundledLibrary()
                    : configuredLibrary(configuredPath);
            System.load(library.toString());
            loaded = true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException exception) {
            throw new JNiceTouchException("Unable to load the JNiceTouch native library", exception);
        }
    }

    private static Path configuredLibrary(String configuredPath) {
        Path library = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(library)) {
            throw new JNiceTouchException("Native library does not exist: " + library);
        }
        return library;
    }

    private static Path extractBundledLibrary() throws IOException {
        Path directory = Files.createTempDirectory("jnicetouch-native-");
        Path library = directory.resolve("libjnicetouch.dylib");
        try (InputStream source = NativeLibraryLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (source == null) {
                throw new IOException("Bundled native library is missing: " + RESOURCE_PATH);
            }
            Files.copy(source, library, StandardCopyOption.REPLACE_EXISTING);
        }
        library.toFile().deleteOnExit();
        directory.toFile().deleteOnExit();
        return library;
    }
}
