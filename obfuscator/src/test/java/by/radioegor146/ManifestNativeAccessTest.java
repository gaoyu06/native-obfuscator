package by.radioegor146;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ManifestNativeAccessTest {
    @TempDir
    Path tempDir;

    @Test
    public void runEmitsEnableNativeAccessAndPreservesMainClass() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "example/Program");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Program.class"));
            jar.write(minimalClass());
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        Manifest outManifest = readOutputManifest(output);
        assertEquals("ALL-UNNAMED",
                outManifest.getMainAttributes().getValue("Enable-Native-Access"));
        assertEquals("example/Program",
                outManifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
    }

    @Test
    public void runWithoutInputManifestCreatesMinimalManifest() throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(inputJar))) {
            jar.putNextEntry(new ZipEntry("example/Program.class"));
            jar.write(minimalClass());
            jar.closeEntry();
        }

        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false);

        Manifest outManifest = readOutputManifest(output);
        assertEquals("1.0",
                outManifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
        assertEquals("ALL-UNNAMED",
                outManifest.getMainAttributes().getValue("Enable-Native-Access"));
    }

    @Test
    public void existingSpecificEnableNativeAccessIsPreserved() {
        Manifest input = new Manifest();
        input.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        input.getMainAttributes().putValue("Enable-Native-Access", "org.example.mod");

        Manifest result = NativeObfuscator.buildOutputManifest(input);

        assertEquals("org.example.mod",
                result.getMainAttributes().getValue("Enable-Native-Access"));
    }

    @Test
    public void absentInputManifestProducesMinimalManifest() {
        Manifest result = NativeObfuscator.buildOutputManifest(null);

        assertEquals("1.0",
                result.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
        assertEquals("ALL-UNNAMED",
                result.getMainAttributes().getValue("Enable-Native-Access"));
        assertNull(result.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
    }

    private Manifest readOutputManifest(Path output) throws Exception {
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            return jar.getManifest();
        }
    }

    private byte[] minimalClass() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Program";
        owner.superName = "java/lang/Object";

        MethodNode constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(constructor);

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        owner.accept(writer);
        return writer.toByteArray();
    }
}
