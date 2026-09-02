package by.radioegor146;

import by.radioegor146.nativeobfuscator.Native;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NativeAnnotationSupportTest {
    @Test
    public void inheritKeepsCliDefaults() {
        NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                classNode(), method(),
                IrLoweringMode.DIRECT, NativeIntrinsicsMode.SAFE, CompilerBackend.CPP);
        assertEquals(IrLoweringMode.DIRECT, options.getLowering());
        assertEquals(NativeIntrinsicsMode.SAFE, options.getIntrinsics());
        assertEquals(CompilerBackend.CPP, options.getBackend());
        assertEquals(ControlFlowObfuscationMode.OFF, options.getCfObfuscation());
        assertEquals(DirectNativeCallMode.OFF, options.getDirectNative());
    }

    @Test
    public void methodOverridesClassAndCli() {
        ClassNode owner = classNode();
        owner.invisibleAnnotations = annotations("intrinsics", "SAFE",
                "lowering", "EVAL", "backend", "INTERPRETER");
        MethodNode method = method();
        method.invisibleAnnotations = annotations("intrinsics", "OFF",
                "backend", "CPP");
        NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                owner, method,
                IrLoweringMode.DIRECT, NativeIntrinsicsMode.FAST, CompilerBackend.INTERPRETER);
        assertEquals(IrLoweringMode.EVAL, options.getLowering());
        assertEquals(NativeIntrinsicsMode.OFF, options.getIntrinsics());
        assertEquals(CompilerBackend.CPP, options.getBackend());
    }

    @Test
    public void cfObfuscationMethodOverridesCli() {
        MethodNode method = method();
        method.invisibleAnnotations = annotations("cfObfuscation", "BASIC");
        NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                classNode(), method,
                IrLoweringMode.DIRECT, NativeIntrinsicsMode.SAFE, CompilerBackend.CPP,
                ControlFlowObfuscationMode.OFF);
        assertEquals(ControlFlowObfuscationMode.BASIC, options.getCfObfuscation());
    }

    @Test
    public void directNativeMethodOverridesCli() {
        MethodNode method = method();
        method.invisibleAnnotations = annotations("directNative", "ON");
        NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                classNode(), method,
                IrLoweringMode.DIRECT, NativeIntrinsicsMode.SAFE, CompilerBackend.CPP,
                ControlFlowObfuscationMode.OFF, DirectNativeCallMode.OFF);
        assertEquals(DirectNativeCallMode.ON, options.getDirectNative());
    }

    @Test
    public void classOverridesCliWhenMethodInherits() {
        ClassNode owner = classNode();
        owner.invisibleAnnotations = annotations("intrinsics", "FAST");
        NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                owner, method(),
                IrLoweringMode.DIRECT, NativeIntrinsicsMode.OFF, CompilerBackend.CPP);
        assertEquals(NativeIntrinsicsMode.FAST, options.getIntrinsics());
    }

    private static ClassNode classNode() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Annotated";
        owner.superName = "java/lang/Object";
        return owner;
    }

    private static MethodNode method() {
        return new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()V", null, null);
    }

    private static ArrayList<AnnotationNode> annotations(String... namesAndValues) {
        AnnotationNode annotation = new AnnotationNode(Type.getDescriptor(Native.class));
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
            } else if ("directNative".equals(name)) {
                desc = "Lby/radioegor146/nativeobfuscator/NativeDirectNative;";
            } else {
                desc = "Lby/radioegor146/nativeobfuscator/NativeBackend;";
            }
            annotation.values.add(name);
            annotation.values.add(new String[] {desc, enumName});
        }
        return new ArrayList<>(Arrays.asList(annotation));
    }
}
