package by.radioegor146.cmake;

import by.radioegor146.NativeLibraryArtifacts;
import by.radioegor146.zig.ZigTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Links a generated C++ tree for the current host with CMake.
 */
public final class CMakeBuilder {

    private static final Logger logger = LoggerFactory.getLogger(CMakeBuilder.class);

    public static final class BuildRequest {
        public Path cppDir;
        public Path buildDir;
        public Path jdkHome;
        public Path outputJar;
        public Path externalLibsDir;
        public String nativeDir;
    }

    public void build(BuildRequest request) throws IOException, InterruptedException {
        if (request.cppDir == null || !Files.isDirectory(request.cppDir)) {
            throw new IOException("C++ source directory missing: " + request.cppDir);
        }
        if (request.buildDir == null) {
            throw new IOException("CMake build directory is not configured");
        }
        Files.createDirectories(request.buildDir);

        run(Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release",
                request.cppDir.toAbsolutePath().toString()), request);
        run(Arrays.asList("cmake", "--build", ".", "--config", "Release"), request);

        ZigTarget host = ZigTarget.host();
        Path linkedLibrary = findLinkedLibrary(request.buildDir, host.os().libExt());
        NativeLibraryArtifacts.place(linkedLibrary, host.loaderFileName(),
                request.externalLibsDir, request.outputJar, request.nativeDir);
        logger.info("Published host native library {}", host.loaderFileName());
    }

    private static Path findLinkedLibrary(Path buildDir, String extension) throws IOException {
        List<Path> candidates;
        try (Stream<Path> paths = Files.walk(buildDir)) {
            candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(extension))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .contains("native_library"))
                    .collect(Collectors.toList());
        }
        if (candidates.size() != 1) {
            throw new IOException("Expected one linked native_library under " + buildDir +
                    ", found " + candidates);
        }
        return candidates.get(0);
    }

    private static void run(List<String> command, BuildRequest request)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(request.buildDir.toFile());
        builder.redirectErrorStream(true);
        if (request.jdkHome != null) {
            builder.environment().put("JAVA_HOME", request.jdkHome.toAbsolutePath().toString());
        }
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                logger.info("    [cmake] {}", line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("CMake command failed with exit code " + exitCode +
                    ": " + String.join(" ", command) + "\n" + output);
        }
    }
}
