package by.radioegor146.ir;

import by.radioegor146.ControlFlowObfuscationMode;
import by.radioegor146.IrLoweringMode;
import by.radioegor146.MethodContext;
import by.radioegor146.NativeIntrinsicsMode;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import by.radioegor146.CodegenMode;
import by.radioegor146.CompilerBackend;
import by.radioegor146.Platform;
import by.radioegor146.ir.transform.ControlFlowObfuscator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlFlowObfuscationTest {
    @Test
    public void basicModeClearsNonEntryPhis() {
        IrMethod ir = new AsmToIr().build("example/Math", absMethod());
        ControlFlowObfuscator.apply(ir);
        IrBlock entry = ir.getBlocks().get(0);
        for (IrBlock block : ir.getBlocks()) {
            if (block == entry) {
                continue;
            }
            assertTrue(block.getPhis().isEmpty(), block.getName());
        }
    }

    @Test
    public void ifThenJoinCopiesFallThroughValue() {
        IrMethod ir = new AsmToIr().build("example/Math", ifThenJoinMethod());
        ControlFlowObfuscator.apply(ir);
        String cpp = emit(ifThenJoinMethod(), ControlFlowObfuscationMode.BASIC);
        assertTrue(cpp.contains(" = 1;"), cpp);
        assertTrue(cpp.contains(" = 0;"), cpp);
    }

    @Test
    public void joinPhiIsCopiedOnBothBranchArms() {
        IrMethod ir = new AsmToIr().build("example/Math", joinMethod());
        ControlFlowObfuscator.apply(ir);
        String rendered = ir.toString();
        assertTrue(rendered.contains("iconst 1"), rendered);
        assertTrue(rendered.contains("iconst 0"), rendered);
        String cpp = emit(joinMethod(), ControlFlowObfuscationMode.BASIC);
        assertTrue(cpp.contains(" = 1;"), cpp);
        assertTrue(cpp.contains(" = 0;"), cpp);
    }

    @Test
    public void basicModeKeepsEntryFirstAndPermutesTheRest() {
        IrMethod first = new AsmToIr().build("example/Math", absMethod());
        ControlFlowObfuscator.apply(first);
        IrMethod second = new AsmToIr().build("example/Math", absMethod());
        ControlFlowObfuscator.apply(second);
        assertEquals(first.getBlocks().get(0).getId(), 0);
        assertEquals(second.getBlocks().get(0).getId(), 0);
        assertEquals(ids(first), ids(second));
        assertTrue(first.getBlocks().size() >= 3);
        boolean identity = true;
        for (int i = 0; i < first.getBlocks().size(); i++) {
            if (first.getBlocks().get(i).getId() != i) {
                identity = false;
                break;
            }
        }
        assertFalse(identity);
    }

    @Test
    public void basicModeInsertsOpaquePredicateAndDispatcher() {
        IrMethod ir = new AsmToIr().build("example/Math", absMethod());
        ControlFlowObfuscator.apply(ir);
        String rendered = ir.toString();
        assertTrue(rendered.contains("opaque_true"));
        assertTrue(rendered.contains("switch "));
        String cpp = emit(absMethod(), ControlFlowObfuscationMode.BASIC);
        assertTrue(cpp.contains("utils::cf_opaque_true"));
        assertTrue(cpp.contains("switch ("));
    }

    @Test
    public void offModeDoesNotFlatten() {
        String cpp = emit(absMethod(), ControlFlowObfuscationMode.OFF);
        assertFalse(cpp.contains("utils::cf_opaque_true"));
    }

    @Test
    public void processWithBasicWritesDispatcher(@TempDir Path tempDir) throws Exception {
        Path inputJar = tempDir.resolve("input.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        ClassNode owner = owner(absMethod());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(inputJar), manifest)) {
            jar.putNextEntry(new ZipEntry("example/Math.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
        Path output = tempDir.resolve("output");
        new NativeObfuscator().process(inputJar, output,
                Collections.emptyList(), Collections.emptyList(), null,
                null, null, Platform.HOTSPOT, false, false, CodegenMode.IR,
                CompilerBackend.CPP, IrLoweringMode.DIRECT,
                NativeIntrinsicsMode.SAFE, ControlFlowObfuscationMode.BASIC);
        String cpp;
        try (Stream<Path> files = Files.walk(output.resolve("cpp/output"))) {
            Path generated = files
                    .filter(path -> path.getFileName().toString().endsWith(".cpp"))
                    .filter(path -> path.getFileName().toString().contains("Math"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No generated class cpp"));
            cpp = new String(Files.readAllBytes(generated), StandardCharsets.UTF_8);
        }
        assertTrue(cpp.contains("utils::cf_opaque_true"));
        assertTrue(cpp.contains("switch ("));
    }

    @Test
    public void basicModeAcceptsTryCatch() {
        IrMethod ir = new AsmToIr().build("example/Math", divMethod());
        ControlFlowObfuscator.apply(ir);
        String cpp = emit(divMethod(), ControlFlowObfuscationMode.BASIC);
        assertTrue(cpp.contains("switch ("));
        assertTrue(cpp.contains("utils::cf_opaque_true"));
    }

    @Test
    public void evalLoweringSkipsControlFlowObfuscation() {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodNode method = absMethod();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(method), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator), NativeIntrinsicsMode.SAFE)
                .processMethod(context, IrLoweringMode.EVAL, NativeIntrinsicsMode.SAFE,
                        ControlFlowObfuscationMode.BASIC);
        String cpp = context.output.toString();
        assertFalse(cpp.contains("utils::cf_opaque_true"));
    }

    private String emit(MethodNode method, ControlFlowObfuscationMode mode) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(method), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator), NativeIntrinsicsMode.SAFE)
                .processMethod(context, IrLoweringMode.DIRECT, NativeIntrinsicsMode.SAFE, mode);
        return context.output.toString();
    }

    private ClassNode owner(MethodNode method) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Math";
        owner.superName = "java/lang/Object";
        owner.methods.add(method);
        return owner;
    }

    private MethodNode ifThenJoinMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "ifThen", "(Z)I", null, null);
        LabelNode end = new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, end));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(end);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private static List<Integer> ids(IrMethod method) {
        List<Integer> ids = new ArrayList<Integer>();
        for (IrBlock block : method.getBlocks()) {
            ids.add(block.getId());
        }
        return ids;
    }

    private MethodNode joinMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "join", "(Z)I", null, null);
        LabelNode els = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, els));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, end));
        method.instructions.add(els);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode absMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "abs", "(I)I", null, null);
        LabelNode negative = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode divMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "div", "(I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.instructions.add(start);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(end);
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }
}
