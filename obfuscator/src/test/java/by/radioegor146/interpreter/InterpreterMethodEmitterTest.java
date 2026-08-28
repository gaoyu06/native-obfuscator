package by.radioegor146.interpreter;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InterpreterMethodEmitterTest {

    @Test
    public void emitsAddGolden() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(2, compiled.getMaxStack());
        assertEquals(2, compiled.getMaxLocals());
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                4,
                19), compiled.getCode());
    }

    @Test
    public void emitsSubtractGolden() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "subtract", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                5,
                19), compiled.getCode());
    }

    @Test
    public void emitsLoopGoldenAndLowersIinc() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "sumTo", "(I)I", null, null);
        LabelNode loop = new LabelNode();
        LabelNode exit = new LabelNode();

        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(loop);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new IincInsnNode(2, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 3;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(4, compiled.getMaxStack());
        assertEquals(3, compiled.getMaxLocals());
        assertArrayEquals(bytes(
                1, 0, 0, 0, 0, 3, 1, 0,
                1, 0, 0, 0, 0, 3, 2, 0,
                2, 2, 0, 2, 0, 0, 15, 54, 0, 0, 0,
                2, 1, 0, 2, 2, 0, 4, 3, 1, 0,
                2, 2, 0, 1, 1, 0, 0, 0, 4, 3, 2, 0,
                18, 16, 0, 0, 0,
                2, 1, 0, 19), compiled.getCode());
    }

    @Test
    public void emitsMixGolden() {
        MethodNode method = mixMethod();

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(6, compiled.getMaxStack());
        assertEquals(4, compiled.getMaxLocals());
        assertArrayEquals(bytes(
                2, 0, 0,
                1, 185, 121, 55, 158, 21, 3, 2, 0,
                1, 0, 0, 0, 0, 3, 3, 0,
                2, 3, 0, 2, 1, 0, 15, 102, 0, 0, 0,
                2, 2, 0, 2, 2, 0, 1, 6, 0, 0, 0, 22,
                2, 2, 0, 1, 2, 0, 0, 0, 23, 4, 4, 3, 2, 0,
                2, 2, 0, 2, 2, 0, 1, 119, 202, 235, 133, 20, 21, 3, 2, 0,
                2, 2, 0, 1, 13, 0, 0, 0, 24, 3, 2, 0,
                2, 3, 0, 1, 1, 0, 0, 0, 4, 3, 3, 0,
                18, 20, 0, 0, 0,
                2, 2, 0, 19), compiled.getCode());
    }

    @Test
    public void leavesUnsupportedDivisionForCppBackend() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "divide", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 2;

        assertNull(InterpreterMethodEmitter.tryCompile(owner(), method));
    }

    private static MethodNode mixMethod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "mix", "(II)I", null, null);
        LabelNode loop = new LabelNode();
        LabelNode exit = new LabelNode();

        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new LdcInsnNode(0x9E3779B9));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(loop);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new LdcInsnNode(0x85EBCA77));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 13));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Integer", "rotateLeft", "(II)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new IincInsnNode(3, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 4;
        method.maxLocals = 4;
        return method;
    }

    private static ClassNode owner() {
        ClassNode owner = new ClassNode();
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "InterpreterFixture";
        return owner;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
