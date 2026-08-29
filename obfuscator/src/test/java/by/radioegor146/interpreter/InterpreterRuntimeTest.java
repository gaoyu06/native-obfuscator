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
    public void compilesAndExecutesIntegerIsaWithGpp() throws Exception {
        Assumptions.assumeTrue(hasGpp(), "g++ is not available");
        Path directory = Files.createTempDirectory(
                "native-jvm-interpreter-runtime-");
        copyResource("sources/native_jvm_interp.hpp",
                directory.resolve("native_jvm_interp.hpp"));
        copyResource("sources/native_jvm_interp.cpp",
                directory.resolve("native_jvm_interp.cpp"));
        Files.write(directory.resolve("runtime_test.cpp"),
                harness().getBytes(StandardCharsets.UTF_8));

        ProcessResult compile = run(directory, Arrays.asList(
                "g++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                "native_jvm_interp.cpp", "runtime_test.cpp", "-o",
                "runtime_test"));
        assertEquals(0, compile.exitCode, compile.output);

        ProcessResult execute = run(directory, Arrays.asList(
                directory.resolve("runtime_test").toString()));
        assertEquals(0, execute.exitCode, execute.output);
    }

    private static String harness() {
        return "#include \"native_jvm_interp.hpp\"\n" +
                "#include <cstdint>\n" +
                "#include <limits>\n" +
                "\n" +
                "using native_jvm::interp::execution_result;\n" +
                "using native_jvm::interp::frame;\n" +
                "using native_jvm::interp::method_desc;\n" +
                "\n" +
                "static const std::uint8_t add_code[] = { 2,0,0, 2,1,0, 4, 19 };\n" +
                "static const method_desc add_method = { 2, 2, 2, add_code, sizeof(add_code) };\n" +
                "static const method_desc mismatched_add_method = { 1, 2, 2, add_code, sizeof(add_code) };\n" +
                "static const std::uint8_t sub_code[] = { 2,0,0, 2,1,0, 5, 19 };\n" +
                "static const method_desc sub_method = { 2, 2, 2, sub_code, sizeof(sub_code) };\n" +
                "static const std::uint8_t mul_code[] = { 2,0,0, 2,1,0, 20, 19 };\n" +
                "static const method_desc mul_method = { 2, 2, 2, mul_code, sizeof(mul_code) };\n" +
                "static const std::uint8_t and_code[] = { 2,0,0, 2,1,0, 21, 19 };\n" +
                "static const method_desc and_method = { 2, 2, 2, and_code, sizeof(and_code) };\n" +
                "static const std::uint8_t or_code[] = { 2,0,0, 2,1,0, 22, 19 };\n" +
                "static const method_desc or_method = { 2, 2, 2, or_code, sizeof(or_code) };\n" +
                "static const std::uint8_t xor_code[] = { 2,0,0, 2,1,0, 23, 19 };\n" +
                "static const method_desc xor_method = { 2, 2, 2, xor_code, sizeof(xor_code) };\n" +
                "static const std::uint8_t shl_code[] = { 2,0,0, 2,1,0, 24, 19 };\n" +
                "static const method_desc shl_method = { 2, 2, 2, shl_code, sizeof(shl_code) };\n" +
                "static const std::uint8_t shr_code[] = { 2,0,0, 2,1,0, 25, 19 };\n" +
                "static const method_desc shr_method = { 2, 2, 2, shr_code, sizeof(shr_code) };\n" +
                "static const std::uint8_t ushr_code[] = { 2,0,0, 2,1,0, 26, 19 };\n" +
                "static const method_desc ushr_method = { 2, 2, 2, ushr_code, sizeof(ushr_code) };\n" +
                "static const std::uint8_t neg_code[] = { 2,0,0, 27, 19 };\n" +
                "static const method_desc neg_method = { 2, 1, 1, neg_code, sizeof(neg_code) };\n" +
                "static const std::uint8_t div_code[] = { 2,0,0, 2,1,0, 28, 19 };\n" +
                "static const method_desc div_method = { 2, 2, 2, div_code, sizeof(div_code) };\n" +
                "static const std::uint8_t rem_code[] = { 2,0,0, 2,1,0, 29, 19 };\n" +
                "static const method_desc rem_method = { 2, 2, 2, rem_code, sizeof(rem_code) };\n" +
                "static const std::uint8_t sum_code[] = {\n" +
                "    1,0,0,0,0, 3,1,0, 1,0,0,0,0, 3,2,0,\n" +
                "    2,2,0, 2,0,0, 15,54,0,0,0,\n" +
                "    2,1,0, 2,2,0, 4, 3,1,0,\n" +
                "    2,2,0, 1,1,0,0,0, 4, 3,2,0,\n" +
                "    18,16,0,0,0, 2,1,0, 19\n" +
                "};\n" +
                "static const method_desc sum_method = { 2, 4, 3, sum_code, sizeof(sum_code) };\n" +
                "\n" +
                "static bool run_binary(const method_desc &method,\n" +
                "                       std::int32_t a, std::int32_t b,\n" +
                "                       std::int32_t expected) {\n" +
                "    std::int32_t locals[2] = { a, b };\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(method, f, &result) ==\n" +
                "                   execution_result::success &&\n" +
                "           result == expected;\n" +
                "}\n" +
                "\n" +
                "static execution_result run_binary_status(const method_desc &method,\n" +
                "                                          std::int32_t a,\n" +
                "                                          std::int32_t b) {\n" +
                "    std::int32_t locals[2] = { a, b };\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(method, f, &result);\n" +
                "}\n" +
                "\n" +
                "static bool run_unary(const method_desc &method,\n" +
                "                      std::int32_t value,\n" +
                "                      std::int32_t expected) {\n" +
                "    std::int32_t locals[1] = { value };\n" +
                "    std::int32_t stack[1] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(method, f, &result) ==\n" +
                "                   execution_result::success &&\n" +
                "           result == expected;\n" +
                "}\n" +
                "\n" +
                "static bool run_sum(std::int32_t n, std::int32_t expected) {\n" +
                "    std::int32_t locals[3] = { n, 0, 0 };\n" +
                "    std::int32_t stack[4] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(sum_method, f, &result) ==\n" +
                "                   execution_result::success &&\n" +
                "           result == expected;\n" +
                "}\n" +
                "\n" +
                "int main() {\n" +
                "    if (!run_binary(add_method, 7, -3, 4)) return 1;\n" +
                "    if (!run_binary(add_method,\n" +
                "                    std::numeric_limits<std::int32_t>::max(), 1,\n" +
                "                 std::numeric_limits<std::int32_t>::min())) return 2;\n" +
                "    if (!run_binary(sub_method, 3, 8, -5)) return 3;\n" +
                "    if (!run_binary(sub_method,\n" +
                "                    std::numeric_limits<std::int32_t>::min(), 1,\n" +
                "                 std::numeric_limits<std::int32_t>::max())) return 4;\n" +
                "    if (!run_binary(mul_method,\n" +
                "                    std::numeric_limits<std::int32_t>::max(), 2,\n" +
                "                    -2)) return 5;\n" +
                "    if (!run_binary(and_method, 0x5a, 0x3c, 0x18)) return 6;\n" +
                "    if (!run_binary(or_method, 0x5a, 0x3c, 0x7e)) return 7;\n" +
                "    if (!run_binary(xor_method, 0x5a, 0x3c, 0x66)) return 8;\n" +
                "    if (!run_binary(shl_method, 1, 33, 2)) return 9;\n" +
                "    if (!run_binary(shl_method, 1, -1,\n" +
                "                    std::numeric_limits<std::int32_t>::min())) return 10;\n" +
                "    if (!run_binary(shr_method, -8, 34, -2)) return 11;\n" +
                "    if (!run_binary(ushr_method, -1, 1,\n" +
                "                    std::numeric_limits<std::int32_t>::max())) return 12;\n" +
                "    if (!run_binary(ushr_method, -1, -1, 1)) return 13;\n" +
                "    if (!run_unary(neg_method, 7, -7)) return 14;\n" +
                "    if (!run_unary(neg_method,\n" +
                "                   std::numeric_limits<std::int32_t>::min(),\n" +
                "                   std::numeric_limits<std::int32_t>::min())) return 15;\n" +
                "    if (!run_binary(div_method, -7, 3, -2)) return 16;\n" +
                "    if (!run_binary(div_method,\n" +
                "                    std::numeric_limits<std::int32_t>::min(), -1,\n" +
                "                    std::numeric_limits<std::int32_t>::min())) return 17;\n" +
                "    if (run_binary_status(div_method, 1, 0) !=\n" +
                "            execution_result::arithmetic_exception) return 18;\n" +
                "    if (!run_binary(rem_method, -7, 3, -1)) return 19;\n" +
                "    if (!run_binary(rem_method,\n" +
                "                    std::numeric_limits<std::int32_t>::min(), -1,\n" +
                "                    0)) return 20;\n" +
                "    if (run_binary_status(rem_method, 1, 0) !=\n" +
                "            execution_result::arithmetic_exception) return 21;\n" +
                "    if (!run_sum(-3, 0)) return 22;\n" +
                "    if (!run_sum(0, 0)) return 23;\n" +
                "    if (!run_sum(10, 45)) return 24;\n" +
                "    if (run_binary_status(mismatched_add_method, 1, 2) !=\n" +
                "            execution_result::invalid_stream) return 25;\n" +
                "    return 0;\n" +
                "}\n";
    }

    private static void copyResource(String name, Path destination)
            throws IOException {
        try (InputStream input = InterpreterRuntimeTest.class.getClassLoader()
                .getResourceAsStream(name)) {
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

    private static ProcessResult run(Path directory,
                                     java.util.List<String> command)
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
        return new ProcessResult(exitCode,
                new String(output.toByteArray(), StandardCharsets.UTF_8));
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
