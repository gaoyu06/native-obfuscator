package by.radioegor146;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodegenModeTest {
    @TempDir
    Path tempDir;

    @Test
    public void cliDefaultsToLegacy() throws Exception {
        assertEquals(CodegenMode.LEGACY, parseCodegen("input.jar", "output"));
    }

    @Test
    public void cliAcceptsIr() throws Exception {
        assertEquals(CodegenMode.IR,
                parseCodegen("input.jar", "output", "--codegen=ir"));
    }

    @Test
    public void methodProcessingConvenienceDefaultRemainsLegacy() {
        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);

        assertFalse(MethodProcessor.shouldProcess(constructor));
        assertTrue(MethodProcessor.shouldProcess(constructor, CodegenMode.IR));
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

    private byte[] unsupportedIndyConstructorClass() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
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
        constructor.instructions.add(new InsnNode(Opcodes.ICONST_1));
        constructor.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT));
        constructor.instructions.add(new InsnNode(Opcodes.ICONST_0));
        constructor.instructions.add(new InsnNode(Opcodes.FALOAD));
        constructor.instructions.add(new InsnNode(Opcodes.POP));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(constructor);

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
}
