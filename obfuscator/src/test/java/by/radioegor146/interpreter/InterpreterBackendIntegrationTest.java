package by.radioegor146.interpreter;

import by.radioegor146.CodegenMode;
import by.radioegor146.CompilerBackend;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InterpreterBackendIntegrationTest {

    @Test
    public void defaultOutputIsUnchangedAndWidenedIsaFallsBackPerMethod()
            throws Exception {
        Path root = Files.createTempDirectory(
                "native-jvm-interpreter-integration-");
        Path input = root.resolve("fixture.jar");
        writeFixtureJar(input);

        Path defaultOutput = root.resolve("default");
        new NativeObfuscator().process(input, defaultOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        Path explicitCppOutput = root.resolve("explicit-cpp");
        new NativeObfuscator().process(input, explicitCppOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false,
                CodegenMode.LEGACY, CompilerBackend.CPP);

        assertTreesEqual(defaultOutput.resolve("cpp"),
                explicitCppOutput.resolve("cpp"));
        assertFalse(Files.exists(
                defaultOutput.resolve("cpp/native_jvm_interp.cpp")));
        assertFalse(read(defaultOutput.resolve("cpp/CMakeLists.txt"))
                .contains("native_jvm_interp.cpp"));

        Path interpreterOutput = root.resolve("interpreter");
        new NativeObfuscator().process(input, interpreterOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false,
                CodegenMode.LEGACY, CompilerBackend.INTERPRETER);

        assertTrue(Files.exists(
                interpreterOutput.resolve("cpp/native_jvm_interp.cpp")));
        assertTrue(read(interpreterOutput.resolve("cpp/CMakeLists.txt"))
                .contains("native_jvm_interp.cpp"));

        String generated = read(interpreterOutput.resolve(
                "cpp/output/InterpreterFixture_0.cpp"));
        assertEquals(6, occurrences(generated, "_interp_code[]"));
        assertEquals(6, occurrences(generated,
                "native_jvm::interp::execute_i("));
        assertTrue(generated.contains(
                        "utils::throw_re(env, \"java/lang/ArithmeticException\""),
                "division status must become a pending JNI exception");
        assertTrue(generated.contains("cstack0.j = cstack0.i;"),
                "unsupported I2L must use the active legacy codegen");
    }

    @Test
    public void unsupportedInterpreterMethodUsesIrWhenIrIsSelected()
            throws Exception {
        Path root = Files.createTempDirectory(
                "native-jvm-interpreter-ir-fallback-");
        Path input = root.resolve("fixture.jar");
        writeFixtureJar(input);

        Path implicitCppOutput = root.resolve("implicit-cpp");
        new NativeObfuscator().process(input, implicitCppOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CodegenMode.IR);

        Path explicitCppOutput = root.resolve("explicit-cpp");
        new NativeObfuscator().process(input, explicitCppOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false,
                CodegenMode.IR, CompilerBackend.CPP);

        assertTreesEqual(implicitCppOutput.resolve("cpp"),
                explicitCppOutput.resolve("cpp"));

        Path interpreterOutput = root.resolve("interpreter");
        new NativeObfuscator().process(input, interpreterOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false,
                CodegenMode.IR, CompilerBackend.INTERPRETER);

        String generated = read(interpreterOutput.resolve(
                "cpp/output/InterpreterFixture_0.cpp"));
        assertEquals(6, occurrences(generated, "_interp_code[]"));
        assertTrue(generated.contains(
                        "// IR codegen: InterpreterFixture.unsupportedConversion(I)I"),
                "unsupported I2L must use the active IR codegen");
    }

    private static void writeFixtureJar(Path destination) throws IOException {
        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "InterpreterFixture", null, "java/lang/Object", null);

        MethodVisitor add = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        add.visitCode();
        add.visitVarInsn(Opcodes.ILOAD, 0);
        add.visitVarInsn(Opcodes.ILOAD, 1);
        add.visitInsn(Opcodes.IADD);
        add.visitInsn(Opcodes.IRETURN);
        add.visitMaxs(0, 0);
        add.visitEnd();

        MethodVisitor sumTo = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "sumTo", "(I)I", null, null);
        Label loop = new Label();
        Label exit = new Label();
        sumTo.visitCode();
        sumTo.visitInsn(Opcodes.ICONST_0);
        sumTo.visitVarInsn(Opcodes.ISTORE, 1);
        sumTo.visitInsn(Opcodes.ICONST_0);
        sumTo.visitVarInsn(Opcodes.ISTORE, 2);
        sumTo.visitLabel(loop);
        sumTo.visitVarInsn(Opcodes.ILOAD, 2);
        sumTo.visitVarInsn(Opcodes.ILOAD, 0);
        sumTo.visitJumpInsn(Opcodes.IF_ICMPGE, exit);
        sumTo.visitVarInsn(Opcodes.ILOAD, 1);
        sumTo.visitVarInsn(Opcodes.ILOAD, 2);
        sumTo.visitInsn(Opcodes.IADD);
        sumTo.visitVarInsn(Opcodes.ISTORE, 1);
        sumTo.visitIincInsn(2, 1);
        sumTo.visitJumpInsn(Opcodes.GOTO, loop);
        sumTo.visitLabel(exit);
        sumTo.visitVarInsn(Opcodes.ILOAD, 1);
        sumTo.visitInsn(Opcodes.IRETURN);
        sumTo.visitMaxs(0, 0);
        sumTo.visitEnd();

        MethodVisitor multiply = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "multiply", "(II)I", null, null);
        multiply.visitCode();
        multiply.visitVarInsn(Opcodes.ILOAD, 0);
        multiply.visitVarInsn(Opcodes.ILOAD, 1);
        multiply.visitInsn(Opcodes.IMUL);
        multiply.visitInsn(Opcodes.IRETURN);
        multiply.visitMaxs(0, 0);
        multiply.visitEnd();

        MethodVisitor bitwise = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bitwise", "(II)I", null, null);
        bitwise.visitCode();
        bitwise.visitVarInsn(Opcodes.ILOAD, 0);
        bitwise.visitVarInsn(Opcodes.ILOAD, 1);
        bitwise.visitInsn(Opcodes.IAND);
        bitwise.visitVarInsn(Opcodes.ILOAD, 0);
        bitwise.visitVarInsn(Opcodes.ILOAD, 1);
        bitwise.visitInsn(Opcodes.IOR);
        bitwise.visitInsn(Opcodes.IXOR);
        bitwise.visitInsn(Opcodes.IRETURN);
        bitwise.visitMaxs(0, 0);
        bitwise.visitEnd();

        MethodVisitor shift = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "shift", "(II)I", null, null);
        shift.visitCode();
        shift.visitVarInsn(Opcodes.ILOAD, 0);
        shift.visitVarInsn(Opcodes.ILOAD, 1);
        shift.visitInsn(Opcodes.ISHL);
        shift.visitVarInsn(Opcodes.ILOAD, 0);
        shift.visitVarInsn(Opcodes.ILOAD, 1);
        shift.visitInsn(Opcodes.IUSHR);
        shift.visitInsn(Opcodes.IXOR);
        shift.visitInsn(Opcodes.IRETURN);
        shift.visitMaxs(0, 0);
        shift.visitEnd();

        MethodVisitor divide = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "divide", "(II)I", null, null);
        divide.visitCode();
        divide.visitVarInsn(Opcodes.ILOAD, 0);
        divide.visitVarInsn(Opcodes.ILOAD, 1);
        divide.visitInsn(Opcodes.IDIV);
        divide.visitInsn(Opcodes.IRETURN);
        divide.visitMaxs(0, 0);
        divide.visitEnd();

        MethodVisitor unsupportedConversion = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "unsupportedConversion", "(I)I", null, null);
        unsupportedConversion.visitCode();
        unsupportedConversion.visitVarInsn(Opcodes.ILOAD, 0);
        unsupportedConversion.visitInsn(Opcodes.I2L);
        unsupportedConversion.visitInsn(Opcodes.L2I);
        unsupportedConversion.visitInsn(Opcodes.IRETURN);
        unsupportedConversion.visitMaxs(0, 0);
        unsupportedConversion.visitEnd();

        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(destination))) {
            jar.putNextEntry(new JarEntry("InterpreterFixture.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }

    private static void assertTreesEqual(Path expectedRoot, Path actualRoot)
            throws IOException {
        List<Path> expectedFiles;
        try (Stream<Path> paths = Files.walk(expectedRoot)) {
            expectedFiles = paths.filter(Files::isRegularFile)
                    .map(expectedRoot::relativize)
                    .sorted()
                    .collect(Collectors.toList());
        }
        List<Path> actualFiles;
        try (Stream<Path> paths = Files.walk(actualRoot)) {
            actualFiles = paths.filter(Files::isRegularFile)
                    .map(actualRoot::relativize)
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertEquals(expectedFiles, actualFiles);
        for (Path relative : expectedFiles) {
            assertArrayEquals(
                    Files.readAllBytes(expectedRoot.resolve(relative)),
                    Files.readAllBytes(actualRoot.resolve(relative)),
                    relative.toString());
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
