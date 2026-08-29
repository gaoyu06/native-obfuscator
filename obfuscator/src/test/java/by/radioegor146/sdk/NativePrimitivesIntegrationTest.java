/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import by.radioegor146.helpers.ProcessHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativePrimitivesIntegrationTest {

    @Test
    public void generatedLibraryMatchesJdkAndKnownVectors() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("native-primitives-test-");
        Path inputJar = temporaryDirectory.resolve("sdk-test.jar");
        Path outputDirectory = temporaryDirectory.resolve("output");
        Files.createDirectories(outputDirectory);
        createInputJar(inputJar);

        Path javaExecutable = javaExecutable();
        ProcessHelper.ProcessResult plainBenchmark = ProcessHelper.run(
                temporaryDirectory,
                120_000,
                Arrays.asList(
                        javaExecutable.toString(),
                        "-cp",
                        inputJar.toString(),
                        NativeStringsBenchmark.class.getName(),
                        "java",
                        "5",
                        "10"));
        plainBenchmark.check("plain Java string benchmark");

        String nativeDirectory = new NativeObfuscator().process(
                inputJar,
                outputDirectory,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                null,
                null,
                Platform.STD_JAVA,
                false,
                false);

        Path cppDirectory = outputDirectory.resolve("cpp");
        assertTrue(Files.isRegularFile(cppDirectory.resolve("sdk/native_primitives.cpp")));
        assertTrue(Files.isRegularFile(cppDirectory.resolve("sdk/native_strings.cpp")));
        assertTrue(Files.isRegularFile(
                cppDirectory.resolve("sdk/third_party/sha-2/LICENSE.md")));
        assertTrue(Files.isRegularFile(
                cppDirectory.resolve("sdk/third_party/tiny-aes-c/UNLICENSE.txt")));
        String cmakeFile = new String(
                Files.readAllBytes(cppDirectory.resolve("CMakeLists.txt")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(cmakeFile.contains("sdk/native_primitives.cpp"));
        assertTrue(cmakeFile.contains("sdk/native_strings.cpp"));
        assertTrue(cmakeFile.contains("sdk/third_party/sha-2/sha-256.cpp"));
        assertTrue(cmakeFile.contains("sdk/aes_gcm.cpp"));
        assertTrue(cmakeFile.contains("sdk/third_party/tiny-aes-c/aes.cpp"));

        ProcessHelper.run(
                        cppDirectory,
                        120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("SDK CMake configure");
        ProcessHelper.run(
                        cppDirectory,
                        160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("SDK CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Native library was not produced"));
        }
        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        injectLibrary(outputJar, nativeDirectory, library);

        ProcessHelper.ProcessResult result = ProcessHelper.run(
                outputDirectory,
                120_000,
                Arrays.asList(
                        javaExecutable.toString(),
                        "-Xcheck:jni",
                        "-jar",
                        outputJar.toString()));
        result.check("SDK Java verification");
        System.out.print(result.stdout);
        assertTrue(
                result.stdout.contains("NativePrimitivesVerifier: PASS"),
                "Verifier output was: " + result.stdout);

        ProcessHelper.ProcessResult nativeBenchmark = ProcessHelper.run(
                outputDirectory,
                120_000,
                Arrays.asList(
                        javaExecutable.toString(),
                        "-Xcheck:jni",
                        "-cp",
                        outputJar.toString(),
                        NativeStringsBenchmark.class.getName(),
                        "native",
                        "5",
                        "10"));
        nativeBenchmark.check("NativeStrings benchmark");
        System.out.print(plainBenchmark.stdout);
        System.out.print(nativeBenchmark.stdout);
        assertTrue(
                plainBenchmark.stdout.contains("NativeStringsBenchmark: mode=java"),
                "Plain benchmark output was: " + plainBenchmark.stdout);
        assertTrue(
                nativeBenchmark.stdout.contains("NativeStringsBenchmark: mode=native"),
                "Native benchmark output was: " + nativeBenchmark.stdout);
        assertEquals(
                outputField(plainBenchmark.stdout, "checksum="),
                outputField(nativeBenchmark.stdout, "checksum="),
                "Java and NativeStrings benchmark checksums differ");
    }

    private static Path javaExecutable() {
        return java.nio.file.Paths.get(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("windows")
                        ? "java.exe"
                        : "java");
    }

    private static String outputField(String output, String prefix) {
        int start = output.indexOf(prefix);
        if (start < 0) {
            throw new AssertionError("Missing " + prefix + " in output: " + output);
        }
        start += prefix.length();
        int end = output.indexOf(' ', start);
        return end < 0 ? output.substring(start).trim() : output.substring(start, end);
    }

    private static void injectLibrary(
            Path outputJar, String nativeDirectory, Path library) throws IOException {
        URI jarUri = URI.create("jar:" + outputJar.toUri());
        try (FileSystem jar = FileSystems.newFileSystem(
                jarUri, Collections.singletonMap("create", "false"))) {
            Path destination = jar.getPath(nativeDirectory, loaderFileName());
            Files.createDirectories(destination.getParent());
            Files.copy(library, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String loaderFileName() {
        String architecture = System.getProperty("os.arch").toLowerCase();
        String platformName;
        switch (architecture) {
            case "x86_64":
            case "amd64":
                platformName = "x64";
                break;
            case "aarch64":
                platformName = "arm64";
                break;
            case "arm":
                platformName = "arm32";
                break;
            case "x86":
                platformName = "x86";
                break;
            default:
                platformName = "raw" + architecture;
                break;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return platformName + "-linux.so";
        }
        if (osName.contains("win")) {
            return platformName + "-windows.dll";
        }
        if (osName.contains("mac")) {
            return platformName + "-macos.dylib";
        }
        return platformName + "-raw" + osName;
    }

    private static void createInputJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS,
                NativePrimitivesVerifier.class.getName());

        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            writeClass(output, NativePrimitivesVerifier.class);
            writeClass(output, NativePrimitivesVerifier.ThrowingRunnable.class);
            writeClass(output, NativeStringsBenchmark.class);
            writeClass(output, NativePrimitives.class);
            writeClass(output, NativeStrings.class);
        }
    }

    private static void writeClass(JarOutputStream output, Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        output.putNextEntry(new JarEntry(resourceName));
        try (InputStream input = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Class resource is missing: " + resourceName);
            }
            byte[] buffer = new byte[4096];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                output.write(buffer, 0, read);
            }
        }
        output.closeEntry();
    }
}
