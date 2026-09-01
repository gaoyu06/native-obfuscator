package by.radioegor146;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodegenModeTest {
    @TempDir
    Path tempDir;

    @Test
    public void cliDefaultsToIr() throws Exception {
        assertEquals(CodegenMode.IR, parseCodegen("input.jar", "output"));
    }

    @Test
    public void cliAcceptsIr() throws Exception {
        assertEquals(CodegenMode.IR,
                parseCodegen("input.jar", "output", "--codegen=ir"));
    }

    @Test
    public void cliRejectsRemovedLegacyCodegen() {
        CommandLine.ParameterException error = assertThrows(
                CommandLine.ParameterException.class,
                () -> parseCodegen("input.jar", "output", "--codegen=legacy"));
        assertTrue(error.getMessage().toLowerCase().contains("legacy")
                || error.getMessage().toLowerCase().contains("ir"));
    }

    @Test
    public void cliDefaultsToDirectIrLowering() throws Exception {
        assertEquals(IrLoweringMode.DIRECT,
                parseIrLowering("input.jar", "output", "--codegen=ir"));
    }

    @Test
    public void cliAcceptsEvaluatorIrLoweringCaseInsensitively() throws Exception {
        assertEquals(IrLoweringMode.EVAL,
                parseIrLowering("--codegen=ir", "--ir-lower=EvAl",
                        "input.jar", "output"));
    }

    @Test
    public void methodProcessingIncludesConstructors() {
        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);

        assertTrue(MethodProcessor.shouldProcess(constructor));
    }

    @Test
    public void rejectedIrConstructorRestoresOriginalInvokedynamic() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Fallback.class"));
            jar.write(unsupportedIndyConstructorClass());
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CodegenMode.IR);

        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            new ClassReader(jar.getInputStream(
                    jar.getJarEntry("example/Fallback.class"))).accept(transformed, 0);
        }
        MethodNode constructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);

        boolean hasInvokeDynamic = false;
        boolean hasMagicMarker = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction
                : constructor.instructions.toArray()) {
            hasInvokeDynamic |= instruction instanceof InvokeDynamicInsnNode;
            if (instruction instanceof MethodInsnNode) {
                hasMagicMarker |= ((MethodInsnNode) instruction).owner
                        .startsWith("native/magic/");
            }
        }
        assertTrue(hasInvokeDynamic);
        assertFalse(hasMagicMarker);
    }

    @Test
    public void rejectedIrMethodRestoresOriginalBytecode() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/FallbackMethod.class"));
            jar.write(unsupportedCondyMethodClass());
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            new ClassReader(jar.getInputStream(
                    jar.getJarEntry("example/FallbackMethod.class")))
                    .accept(transformed, 0);
        }
        MethodNode method = transformed.methods.stream()
                .filter(candidate -> "load".equals(candidate.name))
                .findFirst().orElseThrow(AssertionError::new);

        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        boolean hasConstantDynamic = false;
        boolean hasMagicMarker = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction
                : method.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode) {
                hasConstantDynamic |= ((LdcInsnNode) instruction).cst
                        instanceof ConstantDynamic;
            }
            if (instruction instanceof MethodInsnNode) {
                hasMagicMarker |= ((MethodInsnNode) instruction).owner
                        .startsWith("native/magic/");
            }
        }
        assertTrue(hasConstantDynamic);
        assertFalse(hasMagicMarker);
    }

    @Test
    public void defaultProcessEmitsIrCodegen() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Adder.class"));
            jar.write(simpleAddClass());
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        String cpp = new String(Files.readAllBytes(
                output.resolve("cpp/output/example_Adder_0.cpp")));
        assertTrue(cpp.contains("// IR codegen: example/Adder.add(II)I"));
        assertFalse(cpp.contains("cstack"));
    }

    @Test
    public void malformedJsrRetLeavesMethodJavaAndContinuesClass() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/JsrMix.class"));
            jar.write(malformedJsrWithSiblingAddClass(
                    Opcodes.V1_6, "example/JsrMix"));
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            new ClassReader(jar.getInputStream(
                    jar.getJarEntry("example/JsrMix.class")))
                    .accept(transformed, 0);
        }
        MethodNode malformed = transformed.methods.stream()
                .filter(candidate -> "malformedRet".equals(candidate.name))
                .findFirst().orElseThrow(AssertionError::new);
        MethodNode add = transformed.methods.stream()
                .filter(candidate -> "add".equals(candidate.name))
                .findFirst().orElseThrow(AssertionError::new);

        assertEquals(0, malformed.access & Opcodes.ACC_NATIVE);
        boolean hasRet = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction
                : malformed.instructions.toArray()) {
            hasRet |= instruction.getOpcode() == Opcodes.RET;
        }
        assertTrue(hasRet);
        assertTrue((add.access & Opcodes.ACC_NATIVE) != 0);
        String cpp = new String(Files.readAllBytes(
                output.resolve("cpp/output/example_JsrMix_0.cpp")));
        assertTrue(cpp.contains("// IR codegen: example/JsrMix.add(II)I"));
    }

    @Test
    public void preJava5MalformedJsrRetRaisesToClassLiteralVersion() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/OldJsrMix.class"));
            jar.write(malformedJsrWithSiblingAddClass(Opcodes.V1_4, "example/OldJsrMix"));
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            new ClassReader(jar.getInputStream(
                    jar.getJarEntry("example/OldJsrMix.class")))
                    .accept(transformed, 0);
        }
        assertTrue((transformed.version & 0xFFFF) >= Opcodes.V1_5);
        assertTrue((transformed.version & 0xFFFF) < Opcodes.V1_7);
        MethodNode malformed = transformed.methods.stream()
                .filter(candidate -> "malformedRet".equals(candidate.name))
                .findFirst().orElseThrow(AssertionError::new);
        MethodNode add = transformed.methods.stream()
                .filter(candidate -> "add".equals(candidate.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(0, malformed.access & Opcodes.ACC_NATIVE);
        assertTrue((add.access & Opcodes.ACC_NATIVE) != 0);
    }

    private byte[] unsupportedIndyConstructorClass() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V11;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Fallback";
        owner.superName = "java/lang/Object";

        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner.name,
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false);
        constructor.instructions.add(new InvokeDynamicInsnNode(
                "dynamic", "()Ljava/lang/Object;", bootstrap));
        constructor.instructions.add(new InsnNode(Opcodes.POP));
        // Field-kind ConstantDynamic stays fail-closed so this constructor is
        // restored to the original invokedynamic instead of preprocessor markers.
        constructor.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "constant", "Ljava/lang/Object;",
                new Handle(Opcodes.H_GETSTATIC, "example/Bootstrap", "value",
                        "Ljava/lang/Object;", false))));
        constructor.instructions.add(new InsnNode(Opcodes.POP));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(constructor);

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private byte[] unsupportedCondyMethodClass() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V11;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/FallbackMethod";
        owner.superName = "java/lang/Object";

        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "load", "()Ljava/lang/Object;", null, null);
        method.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "constant", "Ljava/lang/Object;",
                new Handle(Opcodes.H_GETSTATIC, "example/Bootstrap", "value",
                        "Ljava/lang/Object;", false))));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        owner.methods.add(method);

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private byte[] simpleAddClass() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Adder";
        owner.superName = "java/lang/Object";

        MethodNode add = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        add.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        add.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        add.instructions.add(new InsnNode(Opcodes.IADD));
        add.instructions.add(new InsnNode(Opcodes.IRETURN));
        owner.methods.add(add);

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private byte[] malformedJsrWithSiblingAddClass(int version, String name) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = version;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = name;
        owner.superName = "java/lang/Object";

        MethodNode malformed = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "malformedRet", "()V", null, null);
        malformed.instructions.add(new VarInsnNode(Opcodes.RET, 0));
        malformed.instructions.add(new InsnNode(Opcodes.RETURN));
        malformed.maxLocals = 1;
        malformed.maxStack = 0;
        owner.methods.add(malformed);

        MethodNode add = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        add.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        add.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        add.instructions.add(new InsnNode(Opcodes.IADD));
        add.instructions.add(new InsnNode(Opcodes.IRETURN));
        owner.methods.add(add);

        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private CodegenMode parseCodegen(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("codegenMode");
        field.setAccessible(true);
        return (CodegenMode) field.get(runner);
    }

    private IrLoweringMode parseIrLowering(String... args) throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true).parseArgs(args);
        Field field = Main.NativeObfuscatorRunner.class
                .getDeclaredField("irLoweringMode");
        field.setAccessible(true);
        return (IrLoweringMode) field.get(runner);
    }
}
