package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.NativeIntrinsicsMode;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeSdkReplacementTest {
    @Test
    public void replacesAnnotationSdkSha256EvenWhenIntrinsicsAreOff() {
        String cpp = emit(sha256Method(
                "by/radioegor146/nativeobfuscator/NativePrimitives"),
                NativeIntrinsicsMode.OFF);
        assertTrue(cpp.contains("native_obfuscator::sdk::sha256"));
        assertTrue(cpp.contains("(jbyteArray)"));
        assertFalse(cpp.contains("CallStaticObjectMethod"));
    }

    @Test
    public void replacesLegacySdkSha256Owner() {
        String cpp = emit(sha256Method("by/radioegor146/sdk/NativePrimitives"),
                NativeIntrinsicsMode.SAFE);
        assertTrue(cpp.contains("native_obfuscator::sdk::sha256"));
        assertFalse(cpp.contains("CallStaticObjectMethod"));
    }

    @Test
    public void replacesAnnotationSdkStringHashCode() {
        IrMethod ir = new AsmToIr(NativeIntrinsicsMode.OFF)
                .build("example/Math", stringHashMethod());
        assertTrue(ir.toString().contains("sdk_string_hash_code"));
        String cpp = emit(stringHashMethod(), NativeIntrinsicsMode.OFF);
        assertTrue(cpp.contains("native_jvm::strings::hash_code"));
        assertFalse(cpp.contains("CallStaticIntMethod"));
    }

    private String emit(MethodNode method, NativeIntrinsicsMode mode) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(method), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator), mode).processMethod(context);
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

    private MethodNode sha256Method(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "digest", "([B)[B", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                owner, "sha256", "([B)[B", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode stringHashMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "hash", "(Ljava/lang/String;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "by/radioegor146/nativeobfuscator/NativeStrings",
                "hashCode", "(Ljava/lang/String;)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }
}
