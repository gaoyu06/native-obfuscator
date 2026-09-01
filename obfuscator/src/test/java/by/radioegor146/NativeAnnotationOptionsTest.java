package by.radioegor146;

import by.radioegor146.nativeobfuscator.Native;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeAnnotationOptionsTest {
    @TempDir
    Path tempDir;

    @Test
    public void classIntrinsicsOffKeepsStringLengthAsInvoke() throws Exception {
        Path output = process(true, classWithMethods(
                annotations("intrinsics", "OFF"),
                stringLengthMethod(null)));
        String cpp = generatedClassCpp(output);
        assertTrue(cpp.contains("CallIntMethod"));
        assertFalse(cpp.contains("GetStringLength"));
    }

    @Test
    public void attributesApplyWhenAnnotationsFlagIsOff() throws Exception {
        Path output = process(false, classWithMethods(
                null,
                stringLengthMethod(annotations("intrinsics", "OFF"))));
        String cpp = generatedClassCpp(output);
        assertTrue(cpp.contains("CallIntMethod"));
        assertFalse(cpp.contains("GetStringLength"));
    }

    @Test
    public void methodFastOverridesClassOffForBitCount() throws Exception {
        Path output = process(true, classWithMethods(
                annotations("intrinsics", "OFF"),
                stringLengthMethod(null),
                bitCountMethod(annotations("intrinsics", "FAST"))));
        String cpp = generatedClassCpp(output);
        assertTrue(cpp.contains("CallIntMethod"));
        assertTrue(cpp.contains("utils::bit_count_i"));
        assertFalse(cpp.contains("GetStringLength"));
    }

    @Test
    public void evalAnnotationCopiesEvaluatorRuntimeWhenCliIsDirect() throws Exception {
        Path output = process(true, classWithMethods(
                annotations("lowering", "EVAL"),
                stringLengthMethod(null)));
        assertTrue(Files.isRegularFile(output.resolve("cpp/native_jvm_eval.cpp")));
        String cmake = new String(Files.readAllBytes(
                output.resolve("cpp/CMakeLists.txt")), StandardCharsets.UTF_8);
        assertTrue(cmake.contains("native_jvm_eval.cpp"));
    }

    @Test
    public void generatedJarContainsAnnotationAndLegacySdkClasses() throws Exception {
        Path output = process(true, classWithMethods(
                annotations(),
                stringLengthMethod(null)));
        try (JarFile jar = new JarFile(output.resolve("input.jar").toFile())) {
            assertNotNull(jar.getEntry(
                    "by/radioegor146/nativeobfuscator/NativePrimitives.class"));
            assertNotNull(jar.getEntry(
                    "by/radioegor146/nativeobfuscator/NativeStrings.class"));
            assertNotNull(jar.getEntry(
                    "by/radioegor146/sdk/NativePrimitives.class"));
            assertNotNull(jar.getEntry(
                    "by/radioegor146/sdk/NativeStrings.class"));
        }
    }

    private Path process(boolean useAnnotations, byte[] classBytes) throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Annotated.class"));
            jar.write(classBytes);
            jar.closeEntry();
        }
        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, useAnnotations, false, CodegenMode.IR,
                CompilerBackend.CPP, IrLoweringMode.DIRECT,
                NativeIntrinsicsMode.SAFE);
        return output;
    }

    private static String generatedClassCpp(Path output) throws IOException {
        Path cppOutput = output.resolve("cpp/output");
        try (Stream<Path> files = Files.walk(cppOutput)) {
            Path cpp = files
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .filter(path -> path.getFileName().toString().contains("Annotated"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No generated class cpp"));
            return new String(Files.readAllBytes(cpp), StandardCharsets.UTF_8);
        }
    }

    private static byte[] classWithMethods(ArrayList<AnnotationNode> classAnnotations,
                                           MethodNode... methods) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "example/Annotated";
        node.superName = "java/lang/Object";
        node.invisibleAnnotations = classAnnotations;
        node.methods.addAll(Arrays.asList(methods));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode stringLengthMethod(ArrayList<AnnotationNode> annotations) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "len", "(Ljava/lang/String;)I", null, null);
        method.invisibleAnnotations = annotations;
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private static MethodNode bitCountMethod(ArrayList<AnnotationNode> annotations) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bits", "(I)I", null, null);
        method.invisibleAnnotations = annotations;
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Integer", "bitCount", "(I)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private static ArrayList<AnnotationNode> annotations(String... namesAndValues) {
        AnnotationNode annotation = new AnnotationNode(Type.getDescriptor(Native.class));
        if (namesAndValues.length != 0) {
            annotation.values = new ArrayList<>();
            for (int i = 0; i < namesAndValues.length; i += 2) {
                String name = namesAndValues[i];
                String enumName = namesAndValues[i + 1];
                String desc;
                if ("intrinsics".equals(name)) {
                    desc = "Lby/radioegor146/nativeobfuscator/NativeIntrinsics;";
                } else if ("lowering".equals(name)) {
                    desc = "Lby/radioegor146/nativeobfuscator/NativeLowering;";
                } else if ("cfObfuscation".equals(name)) {
                    desc = "Lby/radioegor146/nativeobfuscator/NativeCfObfuscation;";
                } else {
                    desc = "Lby/radioegor146/nativeobfuscator/NativeBackend;";
                }
                annotation.values.add(name);
                annotation.values.add(new String[] {desc, enumName});
            }
        }
        ArrayList<AnnotationNode> list = new ArrayList<>();
        list.add(annotation);
        return list;
    }
}
