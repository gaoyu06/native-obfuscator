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

public class NativeIntrinsicsTest {
    @Test
    public void safeModeReplacesStringHashCodeCharAtAndArraycopy() {
        String hashCpp = emit(stringHashMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(hashCpp.contains("utils::string_hash_code"));
        assertFalse(hashCpp.contains("CallIntMethod"));

        String charAtCpp = emit(stringCharAtMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(charAtCpp.contains("utils::string_char_at"));
        assertFalse(charAtCpp.contains("CallIntMethod"));

        String emptyCpp = emit(stringIsEmptyMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(emptyCpp.contains("GetStringLength"));
        assertFalse(emptyCpp.contains("CallBooleanMethod"));
        assertFalse(emptyCpp.contains("CallIntMethod"));

        String copyCpp = emit(arraycopyMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(copyCpp.contains("utils::arraycopy"));
        assertFalse(copyCpp.contains("CallStaticVoidMethod"));
    }

    @Test
    public void offModeKeepsStringLengthAsInvoke() {
        IrMethod ir = new AsmToIr(NativeIntrinsicsMode.OFF)
                .build("example/Math", stringLengthMethod());
        assertFalse(ir.toString().contains("stringlength"));
        assertTrue(ir.toString().contains("invokevirtual"));

        String cpp = emit(stringLengthMethod(), NativeIntrinsicsMode.OFF);
        assertTrue(cpp.contains("CallIntMethod"));
        assertFalse(cpp.contains("GetStringLength"));
    }

    @Test
    public void fastModeReplacesIntegerBitCount() {
        String cpp = emit(bitCountMethod(), NativeIntrinsicsMode.FAST);
        assertTrue(cpp.contains("utils::bit_count_i"));
        assertFalse(cpp.contains("CallStaticIntMethod"));
    }

    @Test
    public void safeModeLeavesBitCountAsInvoke() {
        String cpp = emit(bitCountMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(cpp.contains("CallStaticIntMethod"));
        assertFalse(cpp.contains("utils::bit_count_i"));
    }

    @Test
    public void safeModeEmitsWrappingMathAbs() {
        String cpp = emit(mathAbsMethod(), NativeIntrinsicsMode.SAFE);
        assertTrue(cpp.contains("uint32_t"));
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

    private MethodNode stringLengthMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "len", "(Ljava/lang/String;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode stringHashMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "hash", "(Ljava/lang/String;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode stringCharAtMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "ch", "(Ljava/lang/String;I)C", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "charAt", "(I)C", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode stringIsEmptyMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "empty", "(Ljava/lang/String;)Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "isEmpty", "()Z", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode arraycopyMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "copy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 5;
        method.maxStack = 5;
        return method;
    }

    private MethodNode bitCountMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bits", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Integer", "bitCount", "(I)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode mathAbsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "abs", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Math", "abs", "(I)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }
}
