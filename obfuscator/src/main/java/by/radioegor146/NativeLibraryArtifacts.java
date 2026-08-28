package by.radioegor146;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes a linked native library to the requested publication destinations.
 */
public final class NativeLibraryArtifacts {

    private NativeLibraryArtifacts() {
    }

    public static void place(Path source, String fileName, Path externalDirectory,
                             Path outputJar, String nativeDirectory) throws IOException {
        if (externalDirectory == null && outputJar == null) {
            throw new IOException("No native library destination configured");
        }
        if (externalDirectory != null) {
            Files.createDirectories(externalDirectory);
            Files.copy(source, externalDirectory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        if (outputJar != null) {
            if (!Files.isRegularFile(outputJar)) {
                throw new IOException("Output jar not found: " + outputJar);
            }
            writeJarEntry(outputJar, nativeDirectory + "/" + fileName, source);
        }
    }

    private static void writeJarEntry(Path jar, String entryPath, Path source) throws IOException {
        URI uri = URI.create("jar:" + jar.toUri());
        Map<String, String> environment = new HashMap<>();
        environment.put("create", "false");
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, environment)) {
            Path destination = fileSystem.getPath(entryPath);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
