package by.radioegor146.ir;

import by.radioegor146.CodegenMode;
import by.radioegor146.CompilerBackend;
import by.radioegor146.ControlFlowObfuscationMode;
import by.radioegor146.IrLoweringMode;
import by.radioegor146.NativeIntrinsicsMode;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import by.radioegor146.helpers.ProcessHelper;
import by.radioegor146.ir.cf.CfRuntimeHarness;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlFlowObfuscationRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    public void basicModeMatchesJavaForJoinLoopAndCatch() throws Exception {
        Assumptions.assumeTrue(onPath("cmake"), "cmake is required");
        Assumptions.assumeTrue(onPath("g++") || onPath("clang++"),
                "a C++ compiler is required");

        Path inputJar = tempDir.resolve("cf-runtime.jar");
        Path outputDirectory = tempDir.resolve("output");
        createHarnessJar(inputJar);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                tempDir, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain CF harness Java run");
        assertEquals(expectedStdout(), javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.emptyList(), null, "native_library", null,
                Platform.STD_JAVA, false, false, CodegenMode.IR,
                CompilerBackend.CPP, IrLoweringMode.DIRECT,
                NativeIntrinsicsMode.SAFE, ControlFlowObfuscationMode.BASIC);

        Path cppDirectory = outputDirectory.resolve("cpp");
        String generated = readGeneratedCpp(cppDirectory);
        assertTrue(generated.contains("utils::cf_opaque_true"), generated);
        assertTrue(generated.contains("switch ("), generated);

        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("CF CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("CF CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "CF native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native CF harness Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    private static String expectedStdout() {
        return "1" + System.lineSeparator()
                + "0" + System.lineSeparator()
                + "15" + System.lineSeparator()
                + "50" + System.lineSeparator()
                + "-1" + System.lineSeparator();
    }

    private static String readGeneratedCpp(Path cppDirectory) throws IOException {
        Path output = cppDirectory.resolve("output");
        try (Stream<Path> files = Files.walk(output)) {
            Path cpp = files
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .filter(path -> path.getFileName().toString().contains("CfRuntime"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No generated harness cpp"));
            return new String(Files.readAllBytes(cpp), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void createHarnessJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, CfRuntimeHarness.class.getName());
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            writeClass(output, CfRuntimeHarness.class);
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

    private static Path javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe" : "java");
    }

    private static boolean onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        String[] directories = path.split(java.io.File.pathSeparator);
        for (String directory : directories) {
            Path executable = Paths.get(directory, name);
            if (Files.isExecutable(executable)) {
                return true;
            }
            Path windows = Paths.get(directory, name + ".exe");
            if (Files.isExecutable(windows)) {
                return true;
            }
        }
        return false;
    }
}
