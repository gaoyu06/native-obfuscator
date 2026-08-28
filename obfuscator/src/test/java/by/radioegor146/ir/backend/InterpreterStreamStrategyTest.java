package by.radioegor146.ir.backend;

import by.radioegor146.CodegenMode;
import by.radioegor146.IrLoweringMode;
import by.radioegor146.MethodContext;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrMethodCompiler;
import by.radioegor146.ir.UnsupportedIrConstructException;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InterpreterStreamStrategyTest {
    @Test
    public void emitsCompactAddBlobAndThinTrampoline() {
        MethodNode method = addMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context, IrLoweringMode.EVAL);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("static const std::uint8_t ir_method_data[]"));
        assertTrue(cpp.contains("native_jvm::ir_eval::evaluate_i32"));
        assertTrue(cpp.contains("const jint ir_method_args[] = { arg0, arg1 };"));
        assertFalse(cpp.contains("// IR codegen:"));
        assertFalse(cpp.contains("jint v2;"));
        assertFalse(cpp.contains("(uint32_t) arg0 + (uint32_t) arg1"));
        assertFalse(cpp.contains("classloader"));
        assertFalse(cpp.contains("cstack"));
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);

        LoweredMethod lowered = lower(addMethod());
        byte[] data = lowered.getMethodData();
        assertNotNull(data);
        assertEquals(18, data.length);
        assertEquals('N', data[0] & 0xff);
        assertEquals('J', data[1] & 0xff);
        assertEquals('E', data[2] & 0xff);
        assertEquals(1, data[3] & 0xff);
        assertEquals(0x10, data[8] & 0xff);
        assertEquals(0x22, data[15] & 0xff);
    }

    @Test
    public void rejectsUnsupportedEvaluatorNodeBeforeMethodMutation() {
        MethodNode method = bitwiseMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);

        assertThrows(UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context, IrLoweringMode.EVAL));

        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
        assertEquals(0, obfuscator.getCachedFields().size());
    }

    @Test
    public void serializesSubtractAndMultiplyOpcodes() {
        byte[] data = lower(subtractMultiplyMethod()).getMethodData();

        assertNotNull(data);
        assertEquals(25, data.length);
        assertEquals(0x11, data[8] & 0xff);
        assertEquals(0x12, data[15] & 0xff);
        assertEquals(0x22, data[22] & 0xff);
    }

    @Test
    public void evalSelectionCopiesRuntimeAndKeepsFixtureMethodsOnEvaluatorPath()
            throws Exception {
        Path directory = Files.createTempDirectory("ir-eval-generation");
        Path inputDirectory = directory.resolve("input");
        Path outputDirectory = directory.resolve("output");
        Files.createDirectories(inputDirectory);
        Files.createDirectories(outputDirectory);
        Path inputJar = inputDirectory.resolve("fixture.jar");
        writeFixtureJar(inputJar);

        new NativeObfuscator().process(inputJar, outputDirectory,
                Collections.emptyList(), Collections.emptyList(), null,
                "native_library", null, Platform.HOTSPOT, false, false,
                CodegenMode.IR, IrLoweringMode.EVAL);

        Path cppDirectory = outputDirectory.resolve("cpp");
        assertTrue(Files.isRegularFile(cppDirectory.resolve("native_jvm_eval.cpp")));
        assertTrue(Files.isRegularFile(cppDirectory.resolve("native_jvm_eval.hpp")));
        String cmake = read(cppDirectory.resolve("CMakeLists.txt"));
        assertTrue(cmake.contains("native_jvm_eval.cpp"));

        Path classSource;
        try (Stream<Path> sources = Files.list(cppDirectory.resolve("output"))) {
            classSource = sources
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .findFirst().orElseThrow(AssertionError::new);
        }
        String generated = read(classSource);
        assertEvaluatorMethod(generated, "// add(II)I");
        assertEvaluatorMethod(generated, "// sumTo(I)I");
    }

    @Test
    public void evaluatorTranslationUnitPassesGppSyntaxSmokeWhenAvailable()
            throws Exception {
        Toolchain toolchain = toolchain();
        Assumptions.assumeTrue(toolchain != null,
                "g++ and JNI headers are required for the syntax smoke");
        Path directory = Files.createTempDirectory("ir-eval-syntax");
        copyEvaluatorResources(directory);
        Path output = directory.resolve("gpp-output.txt");

        int exitCode = run(output, toolchain.gpp.toString(), "-std=c++17",
                "-fsyntax-only", "-I" + toolchain.jniInclude,
                "-I" + toolchain.platformInclude,
                directory.resolve("native_jvm_eval.cpp").toString());

        assertEquals(0, exitCode, "g++ failed:\n" + read(output));
    }

    @Test
    public void sharedEvaluatorRunsAddAndSumToBlobsWhenToolchainAvailable()
            throws Exception {
        Toolchain toolchain = toolchain();
        Assumptions.assumeTrue(toolchain != null,
                "g++ and JNI headers are required for the evaluator runtime smoke");
        Path directory = Files.createTempDirectory("ir-eval-runtime");
        copyEvaluatorResources(directory);

        byte[] addData = lower(addMethod()).getMethodData();
        byte[] sumData = lower(sumToMethod()).getMethodData();
        byte[] subtractMultiplyData = lower(subtractMultiplyMethod()).getMethodData();
        Path harness = directory.resolve("harness.cpp");
        String source = "#include \"native_jvm_eval.hpp\"\n"
                + "#include <iostream>\n"
                + "static const std::uint8_t add_data[] = { " + bytes(addData) + " };\n"
                + "static const std::uint8_t sum_data[] = { " + bytes(sumData) + " };\n"
                + "static const std::uint8_t sub_mul_data[] = { "
                + bytes(subtractMultiplyData) + " };\n"
                + "int main() {\n"
                + "    const jint add_args[] = { 3, 4 };\n"
                + "    const jint sum_args[] = { 6 };\n"
                + "    const jint sub_mul_args[] = { 8, 3 };\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(add_data, "
                + "sizeof(add_data), add_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(sum_data, "
                + "sizeof(sum_data), sum_args, 1) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(sub_mul_data, "
                + "sizeof(sub_mul_data), sub_mul_args, 2) << '\\n';\n"
                + "}\n";
        Files.write(harness, source.getBytes(StandardCharsets.UTF_8));

        Path executable = directory.resolve(
                isWindows() ? "eval-smoke.exe" : "eval-smoke");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        int compileExit = run(compilerOutput, toolchain.gpp.toString(), "-std=c++17",
                "-I" + toolchain.jniInclude, "-I" + toolchain.platformInclude,
                directory.resolve("native_jvm_eval.cpp").toString(),
                harness.toString(), "-o", executable.toString());
        assertEquals(0, compileExit, "g++ failed:\n" + read(compilerOutput));

        Path runtimeOutput = directory.resolve("runtime-output.txt");
        int runtimeExit = run(runtimeOutput, executable.toString());
        assertEquals(0, runtimeExit, "evaluator harness failed:\n" + read(runtimeOutput));
        assertEquals("7\n15\n15\n", normalizeNewlines(read(runtimeOutput)));
    }

    private LoweredMethod lower(MethodNode method) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);
        IrMethod ir = new AsmToIr().build(context.clazz.name, method);
        return new InterpreterStreamStrategy().lower(ir, new LoweringContext(context));
    }

    private MethodContext context(NativeObfuscator obfuscator, MethodNode method) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = "example/EvalFixture";
        owner.access = Opcodes.ACC_PUBLIC;
        return new MethodContext(obfuscator, method, 0, owner, 0);
    }

    private MethodNode addMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode bitwiseMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "and", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode subtractMultiplyMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "subtractMultiply", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode sumToMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "sumTo", "(I)I", null, null);
        LabelNode header = new LabelNode();
        LabelNode exit = new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(header);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new IincInsnNode(2, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, header));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private void writeFixtureJar(Path jar) throws Exception {
        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/EvalFixture",
                null, "java/lang/Object", null);
        writeAdd(writer);
        writeSumTo(writer);
        writer.visitEnd();

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("example/EvalFixture.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }

    private void writeAdd(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add", "(II)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void writeSumTo(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sumTo", "(I)I", null, null);
        org.objectweb.asm.Label header = new org.objectweb.asm.Label();
        org.objectweb.asm.Label exit = new org.objectweb.asm.Label();
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ISTORE, 1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ISTORE, 2);
        method.visitLabel(header);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, exit);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ISTORE, 1);
        method.visitIincInsn(2, 1);
        method.visitJumpInsn(Opcodes.GOTO, header);
        method.visitLabel(exit);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void assertEvaluatorMethod(String generated, String marker) {
        int start = generated.indexOf(marker);
        assertTrue(start >= 0, "Missing generated method " + marker);
        int end = generated.indexOf("\n\n", start);
        String method = generated.substring(start, end < 0 ? generated.length() : end);
        assertTrue(method.contains("native_jvm::ir_eval::evaluate_i32"));
        assertTrue(method.contains("static const std::uint8_t ir_method_data[]"));
        assertFalse(method.contains("cstack"));
        assertFalse(method.contains("(uint32_t)"));
    }

    private void copyEvaluatorResources(Path directory) throws Exception {
        copyResource("/sources/native_jvm_eval.cpp",
                directory.resolve("native_jvm_eval.cpp"));
        copyResource("/sources/native_jvm_eval.hpp",
                directory.resolve("native_jvm_eval.hpp"));
    }

    private void copyResource(String name, Path destination) throws Exception {
        try (InputStream input = InterpreterStreamStrategyTest.class
                .getResourceAsStream(name)) {
            assertNotNull(input, "Missing resource " + name);
            Files.copy(input, destination);
        }
    }

    private Toolchain toolchain() {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        if (gpp == null || !Files.isRegularFile(jniInclude.resolve("jni.h"))
                || !Files.isDirectory(platformInclude)) {
            return null;
        }
        return new Toolchain(gpp, jniInclude, platformInclude);
    }

    private Path executableOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String entry : path.split(File.pathSeparator)) {
            Path candidate = Paths.get(entry).resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String jniPlatformDirectory() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return "linux";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("win")) {
            return "win32";
        }
        return os;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private int run(Path output, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        return process.waitFor();
    }

    private String bytes(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i != 0) {
                result.append(", ");
            }
            result.append(data[i] & 0xff);
        }
        return result.toString();
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    private static final class Toolchain {
        private final Path gpp;
        private final Path jniInclude;
        private final Path platformInclude;

        private Toolchain(Path gpp, Path jniInclude, Path platformInclude) {
            this.gpp = gpp;
            this.jniInclude = jniInclude;
            this.platformInclude = platformInclude;
        }
    }
}
