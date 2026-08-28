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
import org.objectweb.asm.tree.LdcInsnNode;
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
        assertTrue(cpp.contains("const jlong ir_method_args[] = { "
                + "static_cast<jlong>(arg0), static_cast<jlong>(arg1) };"));
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
        MethodNode method = unsupportedUnaryMethod();
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
    public void serializesBitwiseAndShiftOpcodes() {
        int[] bytecodeOpcodes = {
                Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR,
                Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR
        };
        int[] evaluatorOpcodes = {0x13, 0x14, 0x15, 0x16, 0x17, 0x18};

        for (int i = 0; i < bytecodeOpcodes.length; i++) {
            byte[] data = lower(binaryMethod("op" + i, bytecodeOpcodes[i]))
                    .getMethodData();
            assertNotNull(data);
            assertEquals(18, data.length);
            assertEquals(evaluatorOpcodes[i], data[8] & 0xff);
            assertEquals(0x22, data[15] & 0xff);
        }
    }

    @Test
    public void serializesEveryI64OpcodeNumber() {
        byte[] identity = lower(longIdentityMethod()).getMethodData();
        byte[] add = lower(longBinaryMethod("addLong", Opcodes.LADD)).getMethodData();
        byte[] subtract = lower(longBinaryMethod("subtractLong", Opcodes.LSUB)).getMethodData();
        byte[] multiply = lower(longBinaryMethod("multiplyLong", Opcodes.LMUL)).getMethodData();
        byte[] i2l = lower(i2lMethod()).getMethodData();
        byte[] l2i = lower(l2iMethod()).getMethodData();

        assertNotNull(identity);
        assertEquals(21, identity.length);
        assertEquals(0x23, identity[8] & 0xff);
        assertEquals(0x24, identity[13] & 0xff);
        assertEquals(0x28, identity[18] & 0xff);

        assertLongBinaryOpcode(add, 0x25);
        assertLongBinaryOpcode(subtract, 0x26);
        assertLongBinaryOpcode(multiply, 0x27);

        assertNotNull(i2l);
        assertEquals(21, i2l.length);
        assertEquals(0x29, i2l[8] & 0xff);
        assertEquals(0x24, i2l[13] & 0xff);
        assertEquals(0x28, i2l[18] & 0xff);

        assertNotNull(l2i);
        assertEquals(21, l2i.length);
        assertEquals(0x23, l2i[8] & 0xff);
        assertEquals(0x2a, l2i[13] & 0xff);
        assertEquals(0x22, l2i[18] & 0xff);
    }

    @Test
    public void cppEvaluatorUsesTheSameI64OpcodeNumbers() throws Exception {
        String cpp = readResource("/sources/native_jvm_eval.cpp");
        assertTrue(cpp.contains("OP_LLOAD = 0x23;"));
        assertTrue(cpp.contains("OP_LSTORE = 0x24;"));
        assertTrue(cpp.contains("OP_LADD = 0x25;"));
        assertTrue(cpp.contains("OP_LSUB = 0x26;"));
        assertTrue(cpp.contains("OP_LMUL = 0x27;"));
        assertTrue(cpp.contains("OP_LRETURN = 0x28;"));
        assertTrue(cpp.contains("OP_I2L = 0x29;"));
        assertTrue(cpp.contains("OP_L2I = 0x2a;"));
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
        assertEvaluatorMethod(generated, "// run(I)I");
        assertEvaluatorMethod(generated, "// roundTrip(J)J");
        String longMethod = generatedMethod(generated, "// roundTrip(J)J");
        assertTrue(longMethod.contains("native_jvm::ir_eval::evaluate_i64"));
        assertTrue(longMethod.contains("const jlong ir_method_args[] = { arg0 };"));
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
        byte[] andData = lower(binaryMethod("and", Opcodes.IAND)).getMethodData();
        byte[] orData = lower(binaryMethod("or", Opcodes.IOR)).getMethodData();
        byte[] xorData = lower(binaryMethod("xor", Opcodes.IXOR)).getMethodData();
        byte[] shlData = lower(binaryMethod("shl", Opcodes.ISHL)).getMethodData();
        byte[] shrData = lower(binaryMethod("shr", Opcodes.ISHR)).getMethodData();
        byte[] ushrData = lower(binaryMethod("ushr", Opcodes.IUSHR)).getMethodData();
        byte[] kernelData = lower(irFriendlyIntKernelMethod()).getMethodData();
        byte[] longAddData = lower(longBinaryMethod("addLong", Opcodes.LADD)).getMethodData();
        byte[] longSubtractData = lower(
                longBinaryMethod("subtractLong", Opcodes.LSUB)).getMethodData();
        byte[] longMultiplyData = lower(
                longBinaryMethod("multiplyLong", Opcodes.LMUL)).getMethodData();
        byte[] i2lData = lower(i2lMethod()).getMethodData();
        byte[] l2iData = lower(l2iMethod()).getMethodData();
        byte[] longIdentityData = lower(longIdentityMethod()).getMethodData();
        Path harness = directory.resolve("harness.cpp");
        String source = "#include \"native_jvm_eval.hpp\"\n"
                + "#include <iostream>\n"
                + "static const std::uint8_t add_data[] = { " + bytes(addData) + " };\n"
                + "static const std::uint8_t sum_data[] = { " + bytes(sumData) + " };\n"
                + "static const std::uint8_t sub_mul_data[] = { "
                + bytes(subtractMultiplyData) + " };\n"
                + "static const std::uint8_t and_data[] = { " + bytes(andData) + " };\n"
                + "static const std::uint8_t or_data[] = { " + bytes(orData) + " };\n"
                + "static const std::uint8_t xor_data[] = { " + bytes(xorData) + " };\n"
                + "static const std::uint8_t shl_data[] = { " + bytes(shlData) + " };\n"
                + "static const std::uint8_t shr_data[] = { " + bytes(shrData) + " };\n"
                + "static const std::uint8_t ushr_data[] = { " + bytes(ushrData) + " };\n"
                + "static const std::uint8_t kernel_data[] = { "
                + bytes(kernelData) + " };\n"
                + "static const std::uint8_t long_add_data[] = { "
                + bytes(longAddData) + " };\n"
                + "static const std::uint8_t long_sub_data[] = { "
                + bytes(longSubtractData) + " };\n"
                + "static const std::uint8_t long_mul_data[] = { "
                + bytes(longMultiplyData) + " };\n"
                + "static const std::uint8_t i2l_data[] = { " + bytes(i2lData) + " };\n"
                + "static const std::uint8_t l2i_data[] = { " + bytes(l2iData) + " };\n"
                + "static const std::uint8_t long_identity_data[] = { "
                + bytes(longIdentityData) + " };\n"
                + "int main() {\n"
                + "    const jlong add_args[] = { 3, 4 };\n"
                + "    const jlong sum_args[] = { 6 };\n"
                + "    const jlong sub_mul_args[] = { 8, 3 };\n"
                + "    const jlong bitwise_args[] = { 12, 10 };\n"
                + "    const jlong shl_args[] = { 1, 33 };\n"
                + "    const jlong right_shift_args[] = { -4, 33 };\n"
                + "    const jlong kernel_args[] = { 10 };\n"
                + "    const jlong long_add_args[] = { 9223372036854775807LL, 1 };\n"
                + "    const jlong long_sub_args[] = { "
                + "static_cast<jlong>(0x8000000000000000ULL), 1 };\n"
                + "    const jlong long_mul_args[] = { 9223372036854775807LL, 2 };\n"
                + "    const jlong i2l_args[] = { -2147483648LL };\n"
                + "    const jlong l2i_args[] = { 4294967297LL };\n"
                + "    const jlong identity_args[] = { -1234567890123456789LL };\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(add_data, "
                + "sizeof(add_data), add_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(sum_data, "
                + "sizeof(sum_data), sum_args, 1) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(sub_mul_data, "
                + "sizeof(sub_mul_data), sub_mul_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(and_data, "
                + "sizeof(and_data), bitwise_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(or_data, "
                + "sizeof(or_data), bitwise_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(xor_data, "
                + "sizeof(xor_data), bitwise_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(shl_data, "
                + "sizeof(shl_data), shl_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(shr_data, "
                + "sizeof(shr_data), right_shift_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(ushr_data, "
                + "sizeof(ushr_data), right_shift_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(kernel_data, "
                + "sizeof(kernel_data), kernel_args, 1) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i64(long_add_data, "
                + "sizeof(long_add_data), long_add_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i64(long_sub_data, "
                + "sizeof(long_sub_data), long_sub_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i64(long_mul_data, "
                + "sizeof(long_mul_data), long_mul_args, 2) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i64(i2l_data, "
                + "sizeof(i2l_data), i2l_args, 1) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i32(l2i_data, "
                + "sizeof(l2i_data), l2i_args, 1) << '\\n';\n"
                + "    std::cout << native_jvm::ir_eval::evaluate_i64(long_identity_data, "
                + "sizeof(long_identity_data), identity_args, 1) << '\\n';\n"
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
        assertEquals("7\n15\n15\n8\n14\n6\n2\n-2\n2147483646\n"
                        + runIrFriendlyIntKernel(10) + "\n"
                        + "-9223372036854775808\n9223372036854775807\n-2\n"
                        + "-2147483648\n1\n-1234567890123456789\n",
                normalizeNewlines(read(runtimeOutput)));
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

    private MethodNode unsupportedUnaryMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "negate", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode binaryMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode longIdentityMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "roundTrip", "(J)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode longBinaryMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 4;
        method.maxStack = 4;
        return method;
    }

    private MethodNode i2lMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "widen", "(I)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.I2L));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode l2iMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "narrow", "(J)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.L2I));
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

    private MethodNode irFriendlyIntKernelMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "(I)I", null, null);
        LabelNode header = new LabelNode();
        LabelNode exit = new LabelNode();
        method.instructions.add(new LdcInsnNode(0x1234ABCD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(header);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new LdcInsnNode(1664525));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new LdcInsnNode(1013904223));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new LdcInsnNode(13));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new LdcInsnNode(31));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_5));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new IincInsnNode(2, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, header));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 4;
        return method;
    }

    private int runIrFriendlyIntKernel(int rounds) {
        int value = 0x1234ABCD;
        for (int i = 0; i < rounds; i++) {
            value = (value * 1664525 + 1013904223) ^ (value >>> 13);
            value += (i * 31) ^ (value << 5);
        }
        return value;
    }

    private void writeFixtureJar(Path jar) throws Exception {
        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/EvalFixture",
                null, "java/lang/Object", null);
        writeAdd(writer);
        writeSumTo(writer);
        writeIrFriendlyIntKernel(writer);
        writeLongRoundTrip(writer);
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

    private void writeIrFriendlyIntKernel(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(I)I", null, null);
        org.objectweb.asm.Label header = new org.objectweb.asm.Label();
        org.objectweb.asm.Label exit = new org.objectweb.asm.Label();
        method.visitCode();
        method.visitLdcInsn(0x1234ABCD);
        method.visitVarInsn(Opcodes.ISTORE, 1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ISTORE, 2);
        method.visitLabel(header);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IF_ICMPGE, exit);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitLdcInsn(1664525);
        method.visitInsn(Opcodes.IMUL);
        method.visitLdcInsn(1013904223);
        method.visitInsn(Opcodes.IADD);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitLdcInsn(13);
        method.visitInsn(Opcodes.IUSHR);
        method.visitInsn(Opcodes.IXOR);
        method.visitVarInsn(Opcodes.ISTORE, 1);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitLdcInsn(31);
        method.visitInsn(Opcodes.IMUL);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.ICONST_5);
        method.visitInsn(Opcodes.ISHL);
        method.visitInsn(Opcodes.IXOR);
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

    private void writeLongRoundTrip(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "roundTrip", "(J)J", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.LLOAD, 0);
        method.visitVarInsn(Opcodes.LSTORE, 2);
        method.visitVarInsn(Opcodes.LLOAD, 2);
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void assertLongBinaryOpcode(byte[] data, int opcode) {
        assertNotNull(data);
        assertEquals(33, data.length);
        assertEquals(0x23, data[8] & 0xff);
        assertEquals(0x23, data[13] & 0xff);
        assertEquals(opcode, data[18] & 0xff);
        assertEquals(0x24, data[25] & 0xff);
        assertEquals(0x28, data[30] & 0xff);
    }

    private String generatedMethod(String generated, String marker) {
        int start = generated.indexOf(marker);
        assertTrue(start >= 0, "Missing generated method " + marker);
        int end = generated.indexOf("\n\n", start);
        return generated.substring(start, end < 0 ? generated.length() : end);
    }

    private void assertEvaluatorMethod(String generated, String marker) {
        String method = generatedMethod(generated, marker);
        assertTrue(method.contains("native_jvm::ir_eval::evaluate_"));
        assertTrue(method.contains("static const std::uint8_t ir_method_data[]"));
        assertFalse(method.contains("// IR codegen:"));
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

    private String readResource(String name) throws Exception {
        try (InputStream input = InterpreterStreamStrategyTest.class
                .getResourceAsStream(name)) {
            assertNotNull(input, "Missing resource " + name);
            byte[] buffer = new byte[4096];
            StringBuilder result = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) != -1) {
                result.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return result.toString();
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
