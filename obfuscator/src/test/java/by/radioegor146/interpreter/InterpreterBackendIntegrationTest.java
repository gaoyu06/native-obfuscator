package by.radioegor146.interpreter;

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
    public void defaultCppOutputIsUnchangedAndInterpreterFallsBackPerMethod() throws Exception {
        Path root = Files.createTempDirectory("native-jvm-interpreter-integration-");
        Path input = root.resolve("fixture.jar");
        writeFixtureJar(input);

        Path defaultOutput = root.resolve("default");
        new NativeObfuscator().process(input, defaultOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        Path explicitCppOutput = root.resolve("explicit-cpp");
        new NativeObfuscator().process(input, explicitCppOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CompilerBackend.CPP);

        assertTreesEqual(defaultOutput.resolve("cpp"), explicitCppOutput.resolve("cpp"));
        assertFalse(Files.exists(defaultOutput.resolve("cpp/native_jvm_interp.cpp")));
        assertFalse(read(defaultOutput.resolve("cpp/CMakeLists.txt")).contains("native_jvm_interp.cpp"));

        Path interpreterOutput = root.resolve("interpreter");
        new NativeObfuscator().process(input, interpreterOutput,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CompilerBackend.INTERPRETER);

        assertTrue(Files.exists(interpreterOutput.resolve("cpp/native_jvm_interp.cpp")));
        assertTrue(read(interpreterOutput.resolve("cpp/CMakeLists.txt"))
                .contains("native_jvm_interp.cpp"));

        String generated = read(interpreterOutput.resolve("cpp/output/DemoKernel_0.cpp"));
        assertEquals(3, occurrences(generated, "static const std::uint8_t __ngen_b_"));
        assertEquals(3, occurrences(generated, "native_jvm::interp::execute_i("));
        assertTrue(generated.contains("__ngen_b_0_1[]"));
        assertTrue(generated.contains("__ngen_b_0_2[]"));
        assertTrue(generated.contains("__ngen_b_0_3[]"));
        assertTrue(generated.contains(
                "execute_i(__ngen_d_0_3, interp_frame, &interp_result)"));
        assertFalse(generated.contains("// add(II)I"));
        assertFalse(generated.contains("// sumTo(I)I"));
        assertFalse(generated.contains("// mix(II)I"));
        assertFalse(generated.contains("IADD"));
        assertFalse(generated.contains("ILOAD"));
        assertFalse(generated.contains("opcode_decode_table"));

        int mixStart = generated.indexOf("static const std::uint8_t __ngen_b_0_3[]");
        int divideStart = generated.indexOf("// divide(II)I");
        assertTrue(mixStart >= 0 && divideStart > mixStart);
        String mixOutput = generated.substring(mixStart, divideStart);
        assertFalse(mixOutput.contains("mix"));
        assertFalse(mixOutput.contains("jvalue cstack"),
                "mix must not contain a method-specific direct C++ body");
        assertTrue(mixOutput.contains("0xb9, 0x79, 0x37, 0x9e"),
                "32-bit immediates must be emitted as little-endian blob bytes");
        assertTrue(mixOutput.contains("0x77, 0xca, 0xeb, 0x85"),
                "32-bit immediates must be emitted as little-endian blob bytes");
        assertFalse(mixOutput.contains("0x9e3779b9"));
        assertFalse(mixOutput.contains("0x85ebca77"));
        assertFalse(generated.contains("__ngen_b_0_4[]"),
                "unsupported division must remain on the direct C++ backend");

        String runtimeHeader = read(interpreterOutput.resolve("cpp/native_jvm_interp.hpp"));
        String runtimeSource = read(interpreterOutput.resolve("cpp/native_jvm_interp.cpp"));
        assertFalse(runtimeHeader.contains("enum class opcode"));
        assertFalse(runtimeHeader.contains("opcode_decode_table"));
        assertTrue(runtimeSource.contains("opcode_decode_table"));
    }

    private static void writeFixtureJar(Path destination) throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "DemoKernel", null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PRIVATE,
                "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor add = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        add.visitCode();
        add.visitVarInsn(Opcodes.ILOAD, 0);
        add.visitVarInsn(Opcodes.ILOAD, 1);
        add.visitInsn(Opcodes.IADD);
        add.visitInsn(Opcodes.IRETURN);
        add.visitMaxs(0, 0);
        add.visitEnd();

        MethodVisitor sumTo = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
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

        MethodVisitor mix = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "mix", "(II)I", null, null);
        Label mixLoop = new Label();
        Label mixExit = new Label();
        mix.visitCode();
        mix.visitVarInsn(Opcodes.ILOAD, 0);
        mix.visitLdcInsn(0x9E3779B9);
        mix.visitInsn(Opcodes.IXOR);
        mix.visitVarInsn(Opcodes.ISTORE, 2);
        mix.visitInsn(Opcodes.ICONST_0);
        mix.visitVarInsn(Opcodes.ISTORE, 3);
        mix.visitLabel(mixLoop);
        mix.visitVarInsn(Opcodes.ILOAD, 3);
        mix.visitVarInsn(Opcodes.ILOAD, 1);
        mix.visitJumpInsn(Opcodes.IF_ICMPGE, mixExit);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitIntInsn(Opcodes.BIPUSH, 6);
        mix.visitInsn(Opcodes.ISHL);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitInsn(Opcodes.ICONST_2);
        mix.visitInsn(Opcodes.IUSHR);
        mix.visitInsn(Opcodes.IADD);
        mix.visitInsn(Opcodes.IADD);
        mix.visitVarInsn(Opcodes.ISTORE, 2);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitLdcInsn(0x85EBCA77);
        mix.visitInsn(Opcodes.IMUL);
        mix.visitInsn(Opcodes.IXOR);
        mix.visitVarInsn(Opcodes.ISTORE, 2);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitIntInsn(Opcodes.BIPUSH, 13);
        mix.visitMethodInsn(Opcodes.INVOKESTATIC,
                "java/lang/Integer", "rotateLeft", "(II)I", false);
        mix.visitVarInsn(Opcodes.ISTORE, 2);
        mix.visitIincInsn(3, 1);
        mix.visitJumpInsn(Opcodes.GOTO, mixLoop);
        mix.visitLabel(mixExit);
        mix.visitVarInsn(Opcodes.ILOAD, 2);
        mix.visitInsn(Opcodes.IRETURN);
        mix.visitMaxs(0, 0);
        mix.visitEnd();

        MethodVisitor divide = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "divide", "(II)I", null, null);
        divide.visitCode();
        divide.visitVarInsn(Opcodes.ILOAD, 0);
        divide.visitVarInsn(Opcodes.ILOAD, 1);
        divide.visitInsn(Opcodes.IDIV);
        divide.visitInsn(Opcodes.IRETURN);
        divide.visitMaxs(0, 0);
        divide.visitEnd();

        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(destination))) {
            jar.putNextEntry(new JarEntry("DemoKernel.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }

    private static void assertTreesEqual(Path expectedRoot, Path actualRoot) throws IOException {
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
            assertArrayEquals(Files.readAllBytes(expectedRoot.resolve(relative)),
                    Files.readAllBytes(actualRoot.resolve(relative)), relative.toString());
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
