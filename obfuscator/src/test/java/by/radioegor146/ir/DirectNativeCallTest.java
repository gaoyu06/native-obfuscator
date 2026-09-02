package by.radioegor146.ir;

import by.radioegor146.CodegenMode;
import by.radioegor146.CompilerBackend;
import by.radioegor146.ControlFlowObfuscationMode;
import by.radioegor146.DirectNativeCallMode;
import by.radioegor146.IrLoweringMode;
import by.radioegor146.MethodContext;
import by.radioegor146.NativeIntrinsicsMode;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import by.radioegor146.ir.emit.MethodShellEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectNativeCallTest {
    @TempDir
    Path tempDir;

    @Test
    public void defaultOffKeepsCallStatic() {
        String cpp = emit(recurse(), DirectNativeCallMode.OFF, null);
        assertTrue(cpp.contains("CallStaticLongMethod"));
        assertFalse(cpp.contains("PushLocalFrame"));
    }

    @Test
    public void onRewritesSelfRecursionToDirectCppCall() {
        String cpp = emit(recurse(), DirectNativeCallMode.ON, null);
        String name = MethodShellEmitter.cppNativeFunctionName(recurse(), 0);
        assertTrue(cpp.contains(name + "(env, clazz"), cpp);
        assertTrue(cpp.contains("PushLocalFrame"));
        assertTrue(cpp.contains("PopLocalFrame"));
        assertFalse(cpp.contains("CallStaticLongMethod"), cpp);
    }

    @Test
    public void onRewritesSameClassHelperWhenMapped() {
        MethodNode helper = helper();
        MethodNode caller = callerOfHelper();
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put(helper.name + helper.desc,
                MethodShellEmitter.cppNativeFunctionName(helper, 1));
        String cpp = emit(caller, helper, DirectNativeCallMode.ON, names);
        assertTrue(cpp.contains("__ngen_native_inc1(env, clazz"), cpp);
        assertFalse(cpp.contains("CallStaticIntMethod"), cpp);
    }

    @Test
    public void synchronizedCalleeStaysCallStatic() {
        MethodNode callee = synchronizedHelper();
        MethodNode caller = callerOf("locked", "(I)I");
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put(callee.name + callee.desc,
                MethodShellEmitter.cppNativeFunctionName(callee, 1));
        String cpp = emit(caller, callee, DirectNativeCallMode.ON, names);
        assertTrue(cpp.contains("CallStaticIntMethod"), cpp);
        assertFalse(cpp.contains("PushLocalFrame"));
    }

    @Test
    public void objectReturnDirectCallDeclaresTempInsideBlock() {
        MethodNode helper = objectHelper();
        MethodNode caller = objectCaller();
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put(helper.name + helper.desc,
                MethodShellEmitter.cppNativeFunctionName(helper, 1));
        String cpp = emit(caller, helper, DirectNativeCallMode.ON, names);
        int local = cpp.indexOf("jobject direct_local");
        assertTrue(local >= 0, cpp);
        int block = cpp.lastIndexOf('{', local);
        int label = cpp.lastIndexOf("B", local);
        assertTrue(block > label, cpp);
        assertFalse(cpp.contains("CallStaticObjectMethod"), cpp);
    }

    @Test
    public void virtualCallStaysJni() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC, "hash", "()I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        String cpp = emit(method, DirectNativeCallMode.ON, null);
        assertTrue(cpp.contains("CallIntMethod"));
        assertFalse(cpp.contains("PushLocalFrame"));
    }

    @Test
    public void processWithCliOnEmitsForwardDeclAndDirectCall() throws Exception {
        Path output = processJar(DirectNativeCallMode.ON);
        String cpp = generatedClassCpp(output);
        assertTrue(cpp.contains("jint JNICALL __ngen_native_inc"), cpp);
        assertTrue(cpp.contains("__ngen_native_inc"), cpp);
        assertTrue(cpp.contains("PushLocalFrame"), cpp);
        assertFalse(cpp.contains("CallStaticIntMethod"), cpp);
    }

    @Test
    public void processWithCliOffKeepsCallStatic() throws Exception {
        Path output = processJar(DirectNativeCallMode.OFF);
        String cpp = generatedClassCpp(output);
        assertTrue(cpp.contains("CallStaticIntMethod"), cpp);
        assertFalse(cpp.contains("PushLocalFrame"), cpp);
    }

    private Path processJar(DirectNativeCallMode mode) throws Exception {
        ClassNode node = owner();
        node.methods.add(callerOfHelper());
        node.methods.add(helper());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path inputJar = tempDir.resolve("input-" + mode + ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Math.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
        Path output = tempDir.resolve("out-" + mode);
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CodegenMode.IR,
                CompilerBackend.CPP, IrLoweringMode.DIRECT,
                NativeIntrinsicsMode.SAFE, ControlFlowObfuscationMode.OFF, mode);
        return output;
    }

    private static String generatedClassCpp(Path output) throws Exception {
        Path cppOutput = output.resolve("cpp/output");
        try (Stream<Path> files = Files.walk(cppOutput)) {
            Path cpp = files
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .filter(path -> path.getFileName().toString().contains("Math"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No generated class cpp"));
            return new String(Files.readAllBytes(cpp), StandardCharsets.UTF_8);
        }
    }

    private String emit(MethodNode method, DirectNativeCallMode mode,
                        Map<String, String> names) {
        return emit(method, null, mode, names);
    }

    private String emit(MethodNode method, MethodNode extra,
                        DirectNativeCallMode mode, Map<String, String> names) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode owner = owner();
        owner.methods.add(method);
        if (extra != null) {
            owner.methods.add(extra);
        }
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);
        context.directNativeCall = mode;
        context.sameClassDirectNativeNames = names;
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        return context.output.toString();
    }

    private ClassNode owner() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Math";
        owner.superName = "java/lang/Object";
        return owner;
    }

    private MethodNode recurse() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "fact", "(IJ)J", null, null);
        LabelNode done = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, done));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.I2L));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "fact", "(IJ)J", false));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.instructions.add(done);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 3;
        method.maxStack = 6;
        return method;
    }

    private MethodNode helper() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "inc", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode callerOfHelper() {
        return callerOf("inc", "(I)I");
    }

    private MethodNode callerOf(String name, String desc) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", name, desc, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode objectHelper() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "id", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode objectCaller() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        LabelNode skip = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, skip));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "id",
                "(Ljava/lang/String;)Ljava/lang/String;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(skip);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode synchronizedHelper() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                "locked", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }
}
