package by.radioegor146.interpreter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    public void executesEmittedMixAgainstJavaResultsWithGpp() throws Exception {
        Assumptions.assumeTrue(hasGpp(), "g++ is not available");
        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), mixMethod());
        assertNotNull(compiled);

        Path directory = Files.createTempDirectory("native-jvm-interpreter-mix-");
        copyResource("sources/native_jvm_interp.hpp", directory.resolve("native_jvm_interp.hpp"));
        copyResource("sources/native_jvm_interp.cpp", directory.resolve("native_jvm_interp.cpp"));
        Files.write(directory.resolve("mix_test.cpp"),
                mixHarness(compiled).getBytes(StandardCharsets.UTF_8));

        ProcessResult compile = run(directory, Arrays.asList(
                "g++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                "native_jvm_interp.cpp", "mix_test.cpp", "-o", "mix_test"));
        assertEquals(0, compile.exitCode, compile.output);

        ProcessResult execute = run(directory, Arrays.asList(directory.resolve("mix_test").toString()));
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
                "static void write_i32(std::uint8_t *output, std::int32_t value) {\n" +
                "    std::uint32_t bits = static_cast<std::uint32_t>(value);\n" +
                "    output[0] = static_cast<std::uint8_t>(bits);\n" +
                "    output[1] = static_cast<std::uint8_t>(bits >> 8);\n" +
                "    output[2] = static_cast<std::uint8_t>(bits >> 16);\n" +
                "    output[3] = static_cast<std::uint8_t>(bits >> 24);\n" +
                "}\n" +
                "\n" +
                "static bool run_unary_branch(std::uint8_t opcode, std::int32_t value, bool expected) {\n" +
                "    std::uint8_t code[] = {\n" +
                "        1,0,0,0,0, 0,16,0,0,0, 1,0,0,0,0,19, 1,1,0,0,0,19\n" +
                "    };\n" +
                "    write_i32(code + 1, value);\n" +
                "    code[5] = opcode;\n" +
                "    method_desc method = { 1, 1, 1, code, sizeof(code) };\n" +
                "    std::int32_t locals[1] = {};\n" +
                "    std::int32_t stack[1] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(method, f, &result) &&\n" +
                "           result == static_cast<std::int32_t>(expected);\n" +
                "}\n" +
                "\n" +
                "static bool run_binary_branch(std::uint8_t opcode, std::int32_t left,\n" +
                "                              std::int32_t right, bool expected) {\n" +
                "    std::uint8_t code[] = {\n" +
                "        1,0,0,0,0, 1,0,0,0,0, 0,21,0,0,0,\n" +
                "        1,0,0,0,0,19, 1,1,0,0,0,19\n" +
                "    };\n" +
                "    write_i32(code + 1, left);\n" +
                "    write_i32(code + 6, right);\n" +
                "    code[10] = opcode;\n" +
                "    method_desc method = { 1, 2, 1, code, sizeof(code) };\n" +
                "    std::int32_t locals[1] = {};\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(method, f, &result) &&\n" +
                "           result == static_cast<std::int32_t>(expected);\n" +
                "}\n" +
                "\n" +
                "static bool checks_all_branches() {\n" +
                "    return run_unary_branch(6, 0, true) && run_unary_branch(6, 1, false) &&\n" +
                "           run_unary_branch(7, 1, true) && run_unary_branch(7, 0, false) &&\n" +
                "           run_unary_branch(8, -1, true) && run_unary_branch(8, 0, false) &&\n" +
                "           run_unary_branch(9, 0, true) && run_unary_branch(9, -1, false) &&\n" +
                "           run_unary_branch(10, 1, true) && run_unary_branch(10, 0, false) &&\n" +
                "           run_unary_branch(11, 0, true) && run_unary_branch(11, 1, false) &&\n" +
                "           run_binary_branch(12, 2, 2, true) && run_binary_branch(12, 2, 3, false) &&\n" +
                "           run_binary_branch(13, 2, 3, true) && run_binary_branch(13, 2, 2, false) &&\n" +
                "           run_binary_branch(14, 2, 3, true) && run_binary_branch(14, 3, 2, false) &&\n" +
                "           run_binary_branch(15, 3, 2, true) && run_binary_branch(15, 2, 3, false) &&\n" +
                "           run_binary_branch(16, 3, 2, true) && run_binary_branch(16, 2, 3, false) &&\n" +
                "           run_binary_branch(17, 2, 3, true) && run_binary_branch(17, 3, 2, false);\n" +
                "}\n" +
                "\n" +
                "static bool rejects_invalid_streams() {\n" +
                "    static const std::uint8_t unknown_code[] = { 255 };\n" +
                "    static const method_desc unknown_method = { 1, 1, 1, unknown_code, sizeof(unknown_code) };\n" +
                "    static const std::uint8_t truncated_code[] = { 1, 0 };\n" +
                "    static const method_desc truncated_method = { 1, 1, 1, truncated_code, sizeof(truncated_code) };\n" +
                "    static const std::uint8_t bad_goto_code[] = { 18, 5, 0, 0, 0 };\n" +
                "    static const method_desc bad_goto_method = { 1, 1, 1, bad_goto_code, sizeof(bad_goto_code) };\n" +
                "    static const std::uint8_t bad_ifeq_code[] = {\n" +
                "        1,1,0,0,0, 6,255,0,0,0, 1,7,0,0,0, 19\n" +
                "    };\n" +
                "    static const method_desc bad_ifeq_method = { 1, 1, 1, bad_ifeq_code, sizeof(bad_ifeq_code) };\n" +
                "    static const std::uint8_t bad_if_icmpeq_code[] = {\n" +
                "        1,1,0,0,0, 1,2,0,0,0, 12,255,0,0,0, 1,7,0,0,0, 19\n" +
                "    };\n" +
                "    static const method_desc bad_if_icmpeq_method = {\n" +
                "        1, 2, 1, bad_if_icmpeq_code, sizeof(bad_if_icmpeq_code)\n" +
                "    };\n" +
                "    static const std::uint8_t bad_local_code[] = { 2, 1, 0 };\n" +
                "    static const method_desc bad_local_method = { 1, 1, 1, bad_local_code, sizeof(bad_local_code) };\n" +
                "    static const std::uint8_t underflow_code[] = { 4, 19 };\n" +
                "    static const method_desc underflow_method = { 1, 2, 1, underflow_code, sizeof(underflow_code) };\n" +
                "    static const std::uint8_t overflow_code[] = { 1, 0, 0, 0, 0, 19 };\n" +
                "    static const method_desc overflow_method = { 1, 0, 1, overflow_code, sizeof(overflow_code) };\n" +
                "    std::int32_t locals[1] = {};\n" +
                "    std::int32_t stack[2] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return !native_jvm::interp::execute_i(unknown_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(truncated_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(bad_goto_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(bad_ifeq_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(bad_if_icmpeq_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(bad_local_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(underflow_method, f, &result) &&\n" +
                "           !native_jvm::interp::execute_i(overflow_method, f, &result);\n" +
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
                "    if (!checks_all_branches()) return 8;\n" +
                "    if (!rejects_invalid_streams()) return 9;\n" +
                "    return 0;\n" +
                "}\n";
    }

    private static String mixHarness(InterpreterMethodEmitter.CompiledMethod compiled) {
        int[][] pairs = {
                {0, 0},
                {0, 1},
                {1, 4},
                {Integer.MIN_VALUE, 3},
                {0x12345678, 16}
        };
        StringBuilder main = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            int seed = pairs[i][0];
            int rounds = pairs[i][1];
            main.append("    if (!run_mix(")
                    .append(cppInt(seed)).append(", ")
                    .append(cppInt(rounds)).append(", ")
                    .append(cppInt(javaMix(seed, rounds))).append(")) return ")
                    .append(i + 1).append(";\n");
        }

        return "#include \"native_jvm_interp.hpp\"\n" +
                "#include <cstdint>\n" +
                "#include <limits>\n" +
                "\n" +
                "using native_jvm::interp::frame;\n" +
                "using native_jvm::interp::method_desc;\n" +
                "\n" +
                "static const std::uint8_t mix_code[] = { " +
                unsignedBytes(compiled.getCode()) + " };\n" +
                "static const method_desc mix_method = { 1, " +
                compiled.getMaxStack() + ", " + compiled.getMaxLocals() +
                ", mix_code, sizeof(mix_code) };\n" +
                "\n" +
                "static bool run_mix(std::int32_t seed, std::int32_t rounds,\n" +
                "                    std::int32_t expected) {\n" +
                "    std::int32_t locals[" + compiled.getMaxLocals() +
                "] = { seed, rounds, 0, 0 };\n" +
                "    std::int32_t stack[" + compiled.getMaxStack() + "] = {};\n" +
                "    std::int32_t result = 0;\n" +
                "    frame f = { locals, stack };\n" +
                "    return native_jvm::interp::execute_i(mix_method, f, &result) &&\n" +
                "           result == expected;\n" +
                "}\n" +
                "\n" +
                "int main() {\n" +
                main +
                "    return 0;\n" +
                "}\n";
    }

    private static String unsignedBytes(byte[] code) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < code.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(code[i] & 0xff);
        }
        return result.toString();
    }

    private static String cppInt(int value) {
        return value == Integer.MIN_VALUE
                ? "std::numeric_limits<std::int32_t>::min()"
                : Integer.toString(value);
    }

    private static int javaMix(int seed, int rounds) {
        int acc = seed ^ 0x9E3779B9;
        for (int r = 0; r < rounds; r++) {
            acc += (acc << 6) + (acc >>> 2);
            acc ^= acc * 0x85EBCA77;
            acc = Integer.rotateLeft(acc, 13);
        }
        return acc;
    }

    private static ClassNode owner() {
        ClassNode owner = new ClassNode();
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "DemoKernel";
        return owner;
    }

    private static MethodNode mixMethod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "mix", "(II)I", null, null);
        LabelNode loop = new LabelNode();
        LabelNode exit = new LabelNode();

        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new LdcInsnNode(0x9E3779B9));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(loop);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new LdcInsnNode(0x85EBCA77));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 13));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Integer", "rotateLeft", "(II)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new IincInsnNode(3, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 4;
        method.maxLocals = 4;
        return method;
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
