package by.radioegor146.interpreter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterpreterRuntimeTest {

    @Test
    public void compilesAndExecutesAddAndLoopWithGpp() throws Exception {
        Assumptions.assumeTrue(hasGpp(), "g++ is not available");
        Path directory = Files.createTempDirectory("native-jvm-interpreter-runtime-");
        copyResource("sources/native_jvm_interp.hpp", directory.resolve("native_jvm_interp.hpp"));
        copyResource("sources/native_jvm_interp.cpp", directory.resolve("native_jvm_interp.cpp"));
        Files.write(directory.resolve("runtime_test.cpp"), harness().getBytes(StandardCharsets.UTF_8));

        ProcessResult compile = run(directory, Arrays.asList(
                "g++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                "native_jvm_interp.cpp", "runtime_test.cpp", "-o", "runtime_test"));
        assertEquals(0, compile.exitCode, compile.output);

        ProcessResult execute = run(directory, Arrays.asList(directory.resolve("runtime_test").toString()));
        assertEquals(0, execute.exitCode, execute.output);
    }

    private static String harness() {
        return "#include \"native_jvm_interp.hpp\"\n" +
                "#include <cstdint>\n" +
                "#include <limits>\n" +
                "\n" +
                "using native_jvm::interp::frame;\n" +
                "using native_jvm::interp::method_desc;\n" +
                "\n" +
                "static const std::uint8_t add_code[] = { 2,0,0, 2,1,0, 4, 19 };\n" +
                "static const method_desc add_method = { 1, 2, 2, add_code, sizeof(add_code) };\n" +
                "static const std::uint8_t sub_code[] = { 2,0,0, 2,1,0, 5, 19 };\n" +
                "static const method_desc sub_method = { 1, 2, 2, sub_code, sizeof(sub_code) };\n" +
                "static const std::uint8_t sum_code[] = {\n" +
                "    1,0,0,0,0, 3,1,0, 1,0,0,0,0, 3,2,0,\n" +
                "    2,2,0, 2,0,0, 15,54,0,0,0,\n" +
                "    2,1,0, 2,2,0, 4, 3,1,0,\n" +
                "    2,2,0, 1,1,0,0,0, 4, 3,2,0,\n" +
                "    18,16,0,0,0, 2,1,0, 19\n" +
                "};\n" +
                "static const method_desc sum_method = { 1, 4, 3, sum_code, sizeof(sum_code) };\n" +
                "\n" +
                "static bool run_add(std::int32_t a, std::int32_t b, std::int32_t expected) {\n" +
                "    std::int32_t locals[2] = { a, b };\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(add_method, f, &result) && result == expected;\n" +
                "}\n" +
                "\n" +
                "static bool run_sub(std::int32_t a, std::int32_t b, std::int32_t expected) {\n" +
                "    std::int32_t locals[2] = { a, b };\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(sub_method, f, &result) && result == expected;\n" +
                "}\n" +
                "\n" +
                "static bool run_sum(std::int32_t n, std::int32_t expected) {\n" +
                "    std::int32_t locals[3] = { n, 0, 0 };\n" +
                "    std::int32_t stack[4] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(sum_method, f, &result) && result == expected;\n" +
                "}\n" +
                "\n" +
                "int main() {\n" +
                "    if (!run_add(7, -3, 4)) return 1;\n" +
                "    if (!run_add(std::numeric_limits<std::int32_t>::max(), 1,\n" +
                "                 std::numeric_limits<std::int32_t>::min())) return 2;\n" +
                "    if (!run_sub(3, 8, -5)) return 3;\n" +
                "    if (!run_sub(std::numeric_limits<std::int32_t>::min(), 1,\n" +
                "                 std::numeric_limits<std::int32_t>::max())) return 4;\n" +
                "    if (!run_sum(-3, 0)) return 5;\n" +
                "    if (!run_sum(0, 0)) return 6;\n" +
                "    if (!run_sum(10, 45)) return 7;\n" +
                "    return 0;\n" +
                "}\n";
    }

    private static void copyResource(String name, Path destination) throws IOException {
        try (InputStream input = InterpreterRuntimeTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing resource " + name);
            }
            Files.copy(input, destination);
        }
    }

    private static boolean hasGpp() {
        try {
            return run(null, Arrays.asList("g++", "--version")).exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ProcessResult run(Path directory, java.util.List<String> command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory.toFile());
        }
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
        return new ProcessResult(exitCode, new String(output.toByteArray(), StandardCharsets.UTF_8));
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
