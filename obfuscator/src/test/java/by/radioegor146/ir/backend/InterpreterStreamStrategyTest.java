package by.radioegor146.ir.backend;

import by.radioegor146.CodegenMode;
import by.radioegor146.CompilerBackend;
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
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InterpreterStreamStrategyTest {
    @TempDir
    Path tempDir;

    @Test
    public void emitsCompactAddDataAndEvaluatorTrampoline() {
        MethodNode method = intBinaryMethod("add", Opcodes.IADD);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context, IrLoweringMode.EVAL);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("static const std::uint8_t ir_method_data[]"));
        assertTrue(cpp.contains(
                "native_jvm::ir_eval::evaluate_i32(env, ir_method_data"));
        assertFalse(cpp.contains("// IR codegen:"));
        assertFalse(cpp.contains("jint v2;"));
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);

        byte[] data = lower(intBinaryMethod("add", Opcodes.IADD)).getMethodData();
        assertNotNull(data);
        assertEquals(18, data.length);
        assertEquals('N', data[0] & 0xff);
        assertEquals('J', data[1] & 0xff);
        assertEquals('E', data[2] & 0xff);
        assertEquals(1, data[3] & 0xff);
        assertEquals(3, data[4] & 0xff);
        assertEquals(0, data[5] & 0xff);
        assertEquals(2, data[6] & 0xff);
        assertEquals(0, data[7] & 0xff);
        assertEquals(0x10, data[8] & 0xff);
        assertEquals(0x22, data[15] & 0xff);
    }

    @Test
    public void rejectsUnsupportedNodeBeforeShellMutation() {
        MethodNode method = unaryMethod();
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
    public void serializesDocumentedI32BinaryOpcodes() {
        int[] bytecode = {
                Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL,
                Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR,
                Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR
        };
        int[] evaluator = {
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18
        };

        for (int i = 0; i < bytecode.length; i++) {
            byte[] data = lower(intBinaryMethod("intOp" + i, bytecode[i]))
                    .getMethodData();
            assertNotNull(data);
            assertEquals(evaluator[i], data[8] & 0xff);
            assertEquals(0x22, data[15] & 0xff);
        }
    }

    @Test
    public void serializesI64ArithmeticBitwiseAndShiftOpcodes() {
        MethodNode addMethod = longBinaryMethod("longAdd", Opcodes.LADD);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, addMethod);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context, IrLoweringMode.EVAL);
        assertTrue(context.output.toString().contains(
                "native_jvm::ir_eval::evaluate_i64(env, ir_method_data"));
        assertFalse(context.output.toString().contains("// IR codegen:"));

        int[] binaryBytecode = {
                Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR
        };
        int[] binaryEvaluator = {
                0x25, 0x26, 0x27, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f
        };
        for (int i = 0; i < binaryBytecode.length; i++) {
            byte[] data = lower(longBinaryMethod(
                    "longOp" + i, binaryBytecode[i])).getMethodData();
            int expectedLength = binaryBytecode[i] == Opcodes.LDIV
                    || binaryBytecode[i] == Opcodes.LREM ? 68 : 33;
            assertLongMethod(data, 18, binaryEvaluator[i], expectedLength);
        }

        int[] shiftBytecode = {Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR};
        int[] shiftEvaluator = {0x30, 0x31, 0x32};
        for (int i = 0; i < shiftBytecode.length; i++) {
            byte[] data = lower(longShiftMethod(
                    "longShift" + i, shiftBytecode[i])).getMethodData();
            assertLongMethod(data, 13, shiftEvaluator[i], 28);
        }
    }

    @Test
    public void staticLongDivideUsesEvaluatorWithoutStructuredBody() {
        MethodNode method = longBinaryMethod("divide", Opcodes.LDIV);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context, IrLoweringMode.EVAL);

        byte[] data = lower(longBinaryMethod("divide", Opcodes.LDIV)).getMethodData();
        assertLongMethod(data, 18, 0x2b, 68);
        String cpp = context.output.toString();
        assertTrue(cpp.contains("native_jvm::ir_eval::evaluate_i64(env, ir_method_data"));
        assertFalse(cpp.contains("// IR codegen:"));
        assertFalse(cpp.contains("int64_t"));
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);
    }

    @Test
    public void longDivideWithCatchFallsBackBeforeShellMutation() {
        MethodNode method = longDivideCatchMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);
        IrMethod ir = new AsmToIr().build(context.clazz.name, method);
        assertFalse(new InterpreterStreamStrategy().supports(ir));

        assertThrows(UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context, IrLoweringMode.EVAL));
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
    }

    @Test
    public void javaAndCppOpcodeMapsAgree() throws Exception {
        String cpp = readResource("/sources/native_jvm_eval.cpp");
        assertTrue(cpp.contains("OP_CONST_I32 = 0x01;"));
        assertTrue(cpp.contains("OP_IUSHR = 0x18;"));
        assertTrue(cpp.contains("OP_RETURN_I32 = 0x22;"));
        assertTrue(cpp.contains("OP_LLOAD = 0x23;"));
        assertTrue(cpp.contains("OP_L2I = 0x2a;"));
        assertTrue(cpp.contains("OP_LDIV = 0x2b;"));
        assertTrue(cpp.contains("OP_LREM = 0x2c;"));
        assertTrue(cpp.contains("OP_LAND = 0x2d;"));
        assertTrue(cpp.contains("OP_LOR = 0x2e;"));
        assertTrue(cpp.contains("OP_LXOR = 0x2f;"));
        assertTrue(cpp.contains("OP_LSHL = 0x30;"));
        assertTrue(cpp.contains("OP_LSHR = 0x31;"));
        assertTrue(cpp.contains("OP_LUSHR = 0x32;"));
    }

    @Test
    public void cppEvaluatorExecutesLongBitwiseAndMaskedShifts() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        Assumptions.assumeTrue(gpp != null
                && Files.isRegularFile(jniInclude.resolve("jni.h"))
                && Files.isDirectory(platformInclude));

        copyResource("/sources/native_jvm_eval.cpp",
                tempDir.resolve("native_jvm_eval.cpp"));
        copyResource("/sources/native_jvm_eval.hpp",
                tempDir.resolve("native_jvm_eval.hpp"));

        byte[][] streams = {
                lower(longBinaryMethod("and", Opcodes.LAND)).getMethodData(),
                lower(longBinaryMethod("or", Opcodes.LOR)).getMethodData(),
                lower(longBinaryMethod("xor", Opcodes.LXOR)).getMethodData(),
                lower(longShiftMethod("shl", Opcodes.LSHL)).getMethodData(),
                lower(longShiftMethod("shr", Opcodes.LSHR)).getMethodData(),
                lower(longShiftMethod("ushr", Opcodes.LUSHR)).getMethodData()
        };
        long[][] cases = {
                {0xf0L, 0xccL, 0xc0L},
                {0xf0L, 0xccL, 0xfcL},
                {0xf0L, 0xccL, 0x3cL},
                {3L, 65L, 6L},
                {-8L, 65L, -4L},
                {-8L, 65L, 9223372036854775804L}
        };
        Path harness = tempDir.resolve("harness.cpp");
        Files.write(harness, runtimeHarness(streams, cases)
                .getBytes(StandardCharsets.UTF_8));
        Path executable = tempDir.resolve(
                isWindows() ? "evaluator-test.exe" : "evaluator-test");
        Path compileLog = tempDir.resolve("compile.log");
        int compileExit = run(compileLog, gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                tempDir.resolve("native_jvm_eval.cpp").toString(),
                harness.toString(), "-o", executable.toString());
        assertEquals(0, compileExit, read(compileLog));
        Path runLog = tempDir.resolve("run.log");
        assertEquals(0, run(runLog, executable.toString()), read(runLog));
    }

    @Test
    public void cppEvaluatorExecutesLongDivRemJvmEdges() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        Assumptions.assumeTrue(gpp != null
                && Files.isRegularFile(jniInclude.resolve("jni.h"))
                && Files.isDirectory(platformInclude));

        copyResource("/sources/native_jvm_eval.cpp",
                tempDir.resolve("native_jvm_eval.cpp"));
        copyResource("/sources/native_jvm_eval.hpp",
                tempDir.resolve("native_jvm_eval.hpp"));

        byte[][] streams = {
                lower(longBinaryMethod("divide", Opcodes.LDIV)).getMethodData(),
                lower(longBinaryMethod("remainder", Opcodes.LREM)).getMethodData()
        };
        Path harness = tempDir.resolve("long-div-rem-harness.cpp");
        Files.write(harness, longDivRemRuntimeHarness(streams)
                .getBytes(StandardCharsets.UTF_8));
        Path executable = tempDir.resolve(
                isWindows() ? "long-div-rem-test.exe" : "long-div-rem-test");
        Path compileLog = tempDir.resolve("long-div-rem-compile.log");
        int compileExit = run(compileLog, gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                tempDir.resolve("native_jvm_eval.cpp").toString(),
                harness.toString(), "-o", executable.toString());
        assertEquals(0, compileExit, read(compileLog));
        Path runLog = tempDir.resolve("long-div-rem-run.log");
        assertEquals(0, run(runLog, executable.toString()), read(runLog));
    }

    @Test
    public void selectionCopiesRuntimeOnlyForIrEvalAndFallsBackPerMethod()
            throws Exception {
        Path input = tempDir.resolve("input");
        Files.createDirectories(input);
        Path jar = input.resolve("fixture.jar");
        writeFixtureJar(jar);

        Path legacyEval = tempDir.resolve("legacy-eval");
        process(jar, legacyEval, CodegenMode.LEGACY, IrLoweringMode.EVAL);
        assertFalse(Files.exists(legacyEval.resolve("cpp/native_jvm_eval.cpp")));

        Path irDirect = tempDir.resolve("ir-direct");
        process(jar, irDirect, CodegenMode.IR, IrLoweringMode.DIRECT);
        assertFalse(Files.exists(irDirect.resolve("cpp/native_jvm_eval.cpp")));
        String directSource = generatedClassSource(irDirect);
        assertTrue(generatedMethod(directSource, "// add(II)I")
                .contains("// IR codegen: example/EvalFixture.add(II)I"));

        Path irEval = tempDir.resolve("ir-eval");
        process(jar, irEval, CodegenMode.IR, IrLoweringMode.EVAL);
        assertTrue(Files.isRegularFile(irEval.resolve("cpp/native_jvm_eval.cpp")));
        assertTrue(Files.isRegularFile(irEval.resolve("cpp/native_jvm_eval.hpp")));
        assertTrue(read(irEval.resolve("cpp/CMakeLists.txt"))
                .contains("native_jvm_eval.cpp"));

        String evalSource = generatedClassSource(irEval);
        String add = generatedMethod(evalSource, "// add(II)I");
        assertTrue(add.contains("static const std::uint8_t ir_method_data[]"));
        assertTrue(add.contains("native_jvm::ir_eval::evaluate_i32"));
        assertFalse(add.contains("// IR codegen:"));
        assertTrue(generatedMethod(evalSource, "// subtract(II)I")
                .contains("native_jvm::ir_eval::evaluate_i32"));
        assertTrue(generatedMethod(evalSource, "// multiply(II)I")
                .contains("native_jvm::ir_eval::evaluate_i32"));
        String unsupported = generatedMethod(evalSource, "// negate(I)I");
        assertFalse(unsupported.contains("ir_method_data"));
        assertTrue(unsupported.contains("cstack"));
    }

    private void process(Path jar, Path output, CodegenMode codegen,
                         IrLoweringMode lowering) throws Exception {
        new NativeObfuscator().process(jar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, "native_fixture", Platform.HOTSPOT, false, false,
                codegen, CompilerBackend.CPP, lowering);
    }

    private LoweredMethod lower(MethodNode method) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = context(obfuscator, method);
        IrMethod ir = new AsmToIr().build(context.clazz.name, method);
        return new InterpreterStreamStrategy().lower(ir, new LoweringContext(context));
    }

    private MethodContext context(NativeObfuscator obfuscator, MethodNode method) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/EvalFixture";
        owner.superName = "java/lang/Object";
        owner.methods.add(method);
        return new MethodContext(obfuscator, method, 0, owner, 0);
    }

    private MethodNode intBinaryMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unaryMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "negate", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode longBinaryMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 4;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longShiftMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "(JI)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode longDivideCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "catchLongDivide", "(JJ)J", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LDIV));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArithmeticException"));
        method.maxLocals = 5;
        method.maxStack = 4;
        return method;
    }

    private void assertLongMethod(byte[] data, int operationOffset,
                                  int opcode, int length) {
        assertNotNull(data);
        assertEquals(length, data.length);
        assertEquals(0x23, data[8] & 0xff);
        assertEquals(opcode, data[operationOffset] & 0xff);
        assertEquals(0x24, data[length - 8] & 0xff);
        assertEquals(0x28, data[length - 3] & 0xff);
    }

    private void writeFixtureJar(Path jar) throws Exception {
        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/EvalFixture",
                null, "java/lang/Object", null);
        writeIntMethod(writer, "add", Opcodes.IADD);
        writeIntMethod(writer, "subtract", Opcodes.ISUB);
        writeIntMethod(writer, "multiply", Opcodes.IMUL);
        MethodVisitor negate = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "negate", "(I)I", null, null);
        negate.visitCode();
        negate.visitVarInsn(Opcodes.ILOAD, 0);
        negate.visitInsn(Opcodes.INEG);
        negate.visitInsn(Opcodes.IRETURN);
        negate.visitMaxs(0, 0);
        negate.visitEnd();
        writer.visitEnd();

        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("example/EvalFixture.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }

    private void writeIntMethod(ClassWriter writer, String name, int opcode) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, "(II)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(opcode);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private String generatedClassSource(Path output) throws Exception {
        try (Stream<Path> files = Files.list(output.resolve("cpp/output"))) {
            Path source = files
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .filter(path -> path.getFileName().toString()
                            .contains("EvalFixture"))
                    .findFirst().orElseThrow(AssertionError::new);
            return read(source);
        }
    }

    private String generatedMethod(String generated, String marker) {
        int start = generated.indexOf(marker);
        assertTrue(start >= 0, "Missing generated method " + marker);
        int end = generated.indexOf("\n    // ", start + marker.length());
        return generated.substring(start, end < 0 ? generated.length() : end);
    }

    private String runtimeHarness(byte[][] streams, long[][] cases) {
        StringBuilder out = new StringBuilder();
        out.append("#include \"native_jvm_eval.hpp\"\n\n");
        for (int i = 0; i < streams.length; i++) {
            out.append("static const std::uint8_t data").append(i).append("[] = {");
            for (int j = 0; j < streams[i].length; j++) {
                if (j != 0) {
                    out.append(", ");
                }
                out.append(streams[i][j] & 0xff);
            }
            out.append("};\n");
        }
        out.append("\nint main() {\n");
        for (int i = 0; i < cases.length; i++) {
            out.append("    { const jlong args[] = { ")
                    .append(cppLong(cases[i][0])).append(", ")
                    .append(cppLong(cases[i][1])).append(" }; ")
                    .append("if (native_jvm::ir_eval::evaluate_i64(nullptr, data")
                    .append(i).append(", sizeof(data").append(i)
                    .append("), args, 2) != ").append(cppLong(cases[i][2]))
                    .append(") return ").append(i + 1).append("; }\n");
        }
        out.append("    return 0;\n}\n");
        return out.toString();
    }

    private String longDivRemRuntimeHarness(byte[][] streams) {
        StringBuilder out = new StringBuilder();
        out.append("#include \"native_jvm_eval.hpp\"\n");
        out.append("#include <cstring>\n\n");
        for (int i = 0; i < streams.length; i++) {
            out.append("static const std::uint8_t data").append(i).append("[] = {");
            for (int j = 0; j < streams[i].length; j++) {
                if (j != 0) {
                    out.append(", ");
                }
                out.append(streams[i][j] & 0xff);
            }
            out.append("};\n");
        }
        out.append("\nstatic bool saw_arithmetic_exception = false;\n");
        out.append("static const char *exception_message = nullptr;\n");
        out.append("static jclass JNICALL fake_find_class(JNIEnv *, const char *name) {\n");
        out.append("    if (std::strcmp(name, \"java/lang/ArithmeticException\") != 0) ")
                .append("return nullptr;\n");
        out.append("    return reinterpret_cast<jclass>(1);\n");
        out.append("}\n");
        out.append("static jint JNICALL fake_throw_new(JNIEnv *, jclass, ")
                .append("const char *message) {\n");
        out.append("    saw_arithmetic_exception = true;\n");
        out.append("    exception_message = message;\n");
        out.append("    return 0;\n");
        out.append("}\n");
        out.append("static void JNICALL fake_delete_local_ref(JNIEnv *, jobject) {}\n\n");
        out.append("static int check(const std::uint8_t *data, std::size_t size, ")
                .append("jlong left, jlong right, jlong expected, int error) {\n");
        out.append("    const jlong args[] = { left, right };\n");
        out.append("    return native_jvm::ir_eval::evaluate_i64(nullptr, data, size, ")
                .append("args, 2) == expected ? 0 : error;\n");
        out.append("}\n\n");
        out.append("int main() {\n");
        out.append("    int error;\n");
        out.append("    if ((error = check(data0, sizeof(data0), -7, 3, -2, 1))) ")
                .append("return error;\n");
        out.append("    if ((error = check(data1, sizeof(data1), -7, 3, -1, 2))) ")
                .append("return error;\n");
        out.append("    if ((error = check(data0, sizeof(data0), ")
                .append(cppLong(Long.MIN_VALUE)).append(", -1, ")
                .append(cppLong(Long.MIN_VALUE)).append(", 3))) return error;\n");
        out.append("    if ((error = check(data1, sizeof(data1), ")
                .append(cppLong(Long.MIN_VALUE)).append(", -1, 0, 4))) return error;\n");
        out.append("    JNINativeInterface_ functions{};\n");
        out.append("    functions.FindClass = fake_find_class;\n");
        out.append("    functions.ThrowNew = fake_throw_new;\n");
        out.append("    functions.DeleteLocalRef = fake_delete_local_ref;\n");
        out.append("    JNIEnv env{&functions};\n");
        out.append("    const jlong zero_args[] = { 7, 0 };\n");
        out.append("    if (native_jvm::ir_eval::evaluate_i64(&env, data0, ")
                .append("sizeof(data0), zero_args, 2) != 0) return 5;\n");
        out.append("    if (!saw_arithmetic_exception || exception_message == nullptr ")
                .append("|| std::strcmp(exception_message, \"LDIV / by 0\") != 0) return 6;\n");
        out.append("    saw_arithmetic_exception = false;\n");
        out.append("    exception_message = nullptr;\n");
        out.append("    if (native_jvm::ir_eval::evaluate_i64(&env, data1, ")
                .append("sizeof(data1), zero_args, 2) != 0) return 7;\n");
        out.append("    if (!saw_arithmetic_exception || exception_message == nullptr ")
                .append("|| std::strcmp(exception_message, \"LREM % by 0\") != 0) return 8;\n");
        out.append("    return 0;\n");
        out.append("}\n");
        return out.toString();
    }

    private String cppLong(long value) {
        if (value == Long.MIN_VALUE) {
            return "static_cast<jlong>(-9223372036854775807LL - 1LL)";
        }
        return "static_cast<jlong>(" + value + "LL)";
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
            byte[] bytes = new byte[8192];
            StringBuilder result = new StringBuilder();
            int count;
            while ((count = input.read(bytes)) != -1) {
                result.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            }
            return result.toString();
        }
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
        String os = System.getProperty("os.name").toLowerCase();
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
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private int run(Path output, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        return process.waitFor();
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
