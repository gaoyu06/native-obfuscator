package by.radioegor146;

import by.radioegor146.zig.ZigTarget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeLibraryPublicationTest {

    @Test
    public void interpreterWithoutPublicationFlagStillEmitsCppTree() throws Exception {
        Path root = Files.createTempDirectory("native-library-source-output-");
        Path input = createFixtureJar(root);
        Path output = root.resolve("source-output");

        new NativeObfuscator(42L).process(input, output,
                Collections.emptyList(), Collections.emptyList(), fixtureWhiteList(),
                null, null, Platform.HOTSPOT, false, false, CompilerBackend.INTERPRETER);

        assertTrue(Files.isRegularFile(output.resolve("fixture.jar")));
        assertTrue(Files.isRegularFile(output.resolve("cpp/native_jvm_interp.cpp")));
        assertTrue(Files.isRegularFile(output.resolve("cpp/CMakeLists.txt")));
    }

    @Test
    public void publishesLinkedLibraryWithoutInterpreterSourcesAndMatchesJavaOracle()
            throws Exception {
        Assumptions.assumeTrue(commandSucceeds("cmake", "--version"),
                "cmake is not available");
        Assumptions.assumeTrue(commandSucceeds("g++", "--version"),
                "g++ is not available");

        Path root = Files.createTempDirectory("native-library-publication-");
        Path input = createFixtureJar(root);
        Path whiteList = root.resolve("whitelist.txt");
        Files.write(whiteList, fixtureWhiteList(), StandardCharsets.UTF_8);
        Path output = root.resolve("published");

        int exitCode = new CommandLine(new Main.NativeObfuscatorRunner())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute("--backend=interpreter", "--publish-native-lib",
                        "--opcode-seed=424242", "--white-list=" + whiteList,
                        input.toString(), output.toString());
        assertEquals(0, exitCode);

        String loaderFileName = ZigTarget.host().loaderFileName();
        Path publishedJar = output.resolve("fixture.jar");
        Path publishedLibrary = output.resolve(loaderFileName);
        assertTrue(Files.isRegularFile(publishedJar));
        assertTrue(Files.isRegularFile(publishedLibrary));
        assertTrue(Files.size(publishedLibrary) > 0);

        List<Path> publishedFiles;
        try (Stream<Path> paths = Files.walk(output)) {
            publishedFiles = paths.filter(Files::isRegularFile)
                    .map(output::relativize)
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertEquals(Arrays.asList(Paths.get("fixture.jar"), Paths.get(loaderFileName))
                .stream().sorted().collect(Collectors.toList()), publishedFiles);
        assertFalse(publishedFiles.stream().anyMatch(path ->
                path.toString().equals("native_jvm_interp.cpp")));
        assertFalse(publishedFiles.stream().anyMatch(path ->
                path.getFileName().toString().endsWith(".cpp")));

        try (JarFile jar = new JarFile(publishedJar.toFile())) {
            JarEntry libraryEntry = jar.getJarEntry("native0/" + loaderFileName);
            assertNotNull(libraryEntry);
            try (InputStream inputStream = jar.getInputStream(libraryEntry)) {
                assertArrayEquals(Files.readAllBytes(publishedLibrary), readAll(inputStream));
            }
            assertFalse(jar.stream().anyMatch(entry ->
                    entry.getName().endsWith("native_jvm_interp.cpp")));
        }

        ProcessResult oracle = run(Arrays.asList(javaExecutable(), "-jar", input.toString()));
        ProcessResult published = run(Arrays.asList(
                javaExecutable(), "-jar", publishedJar.toString()));
        assertEquals(0, oracle.exitCode, oracle.output);
        assertEquals(0, published.exitCode, published.output);
        assertEquals("4:45", oracle.output.trim());
        assertEquals(oracle.output.trim(), published.output.trim());
    }

    private static Path createFixtureJar(Path root) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required");
        Path source = root.resolve("LinkOnlyFixture.java");
        Files.write(source, (
                "public final class LinkOnlyFixture {\n" +
                "  public static int add(int a, int b) { return a + b; }\n" +
                "  public static int sumTo(int n) {\n" +
                "    int sum = 0;\n" +
                "    for (int i = 0; i < n; i++) sum += i;\n" +
                "    return sum;\n" +
                "  }\n" +
                "  public static void main(String[] args) {\n" +
                "    System.out.print(add(7, -3) + \":\" + sumTo(10));\n" +
                "  }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        int result = compiler.run(null, null, null, "-source", "8", "-target", "8",
                "-d", classes.toString(), source.toString());
        assertEquals(0, result);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "LinkOnlyFixture");
        Path jar = root.resolve("fixture.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
             Stream<Path> paths = Files.walk(classes)) {
            for (Path classFile : paths.filter(Files::isRegularFile)
                    .collect(Collectors.toList())) {
                output.putNextEntry(new JarEntry(classes.relativize(classFile)
                        .toString().replace('\\', '/')));
                Files.copy(classFile, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private static List<String> fixtureWhiteList() {
        List<String> entries = new ArrayList<>();
        entries.add("LinkOnlyFixture");
        entries.add("LinkOnlyFixture#add!(II)I");
        entries.add("LinkOnlyFixture#sumTo!(I)I");
        entries.add("LinkOnlyFixture#<clinit>!()V");
        return entries;
    }

    private static boolean commandSucceeds(String... command) {
        try {
            return run(Arrays.asList(command)).exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", executable)
                .toString();
    }

    private static ProcessResult run(List<String> command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode,
                new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
