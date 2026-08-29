package by.radioegor146.interpreter;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InterpreterMethodEmitterTest {

    @Test
    public void emitsAddGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
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
    public void emitsLongAddGoldenAndUsesTwoSlotLocals() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "addLong", "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxStack = 4;
        method.maxLocals = 4;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(4, InterpreterMethodEmitter.ISA_VERSION);
        assertEquals(4, compiled.getMaxStack());
        assertEquals(4, compiled.getMaxLocals());
        assertArrayEquals(bytes(
                31, 0, 0,
                31, 2, 0,
                33,
                43), compiled.getCode());
    }

    @Test
    public void emitsLongConstantStoreShiftAndNegateGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "constant", "()J", null, null);
        method.instructions.add(new LdcInsnNode(0x0102030405060708L));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.LSHL));
        method.instructions.add(new InsnNode(Opcodes.LNEG));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxStack = 3;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                30, 8, 7, 6, 5, 4, 3, 2, 1,
                32, 0, 0,
                31, 0, 0,
                1, 1, 0, 0, 0,
                39,
                42,
                43), compiled.getCode());
    }

    @Test
    public void emitsReferenceIdentityAndUsesOneSlotLocal() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "identity", "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(4, InterpreterMethodEmitter.ISA_VERSION);
        assertEquals(1, compiled.getMaxStack());
        assertEquals(1, compiled.getMaxLocals());
        assertArrayEquals(bytes(47, 0, 0, 49), compiled.getCode());

        MethodNode arrayIdentity = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "arrayIdentity", "([I)[I", null, null);
        arrayIdentity.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        arrayIdentity.instructions.add(new InsnNode(Opcodes.ARETURN));
        arrayIdentity.maxStack = 1;
        arrayIdentity.maxLocals = 1;
        assertNotNull(InterpreterMethodEmitter.tryCompile(
                owner(), arrayIdentity));
    }

    @Test
    public void emitsAthrowAsAppendedOpcode() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "raise", "(Ljava/lang/Throwable;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.maxStack = 1;
        method.maxLocals = 1;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(4, InterpreterMethodEmitter.ISA_VERSION);
        assertEquals(52, InterpreterMethodEmitter.ATHROW);
        assertArrayEquals(bytes(47, 0, 0, 52), compiled.getCode());
        assertEquals(0, compiled.getExceptionHandlers().length);
    }

    @Test
    public void emitsOrderedTypedAndCatchAllExceptionTable() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "divideOrMinusOne", "(II)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, null));
        method.maxStack = 2;
        method.maxLocals = 3;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                28,
                19,
                48, 2, 0,
                1, -1, -1, -1, -1,
                19), compiled.getCode());
        InterpreterMethodEmitter.ExceptionHandler[] handlers =
                compiled.getExceptionHandlers();
        assertEquals(2, handlers.length);
        assertEquals(0, handlers[0].getStartPc());
        assertEquals(7, handlers[0].getEndPc());
        assertEquals(8, handlers[0].getHandlerPc());
        assertEquals("java/lang/ArithmeticException",
                handlers[0].getCatchType());
        assertEquals(0, handlers[1].getStartPc());
        assertEquals(7, handlers[1].getEndPc());
        assertEquals(8, handlers[1].getHandlerPc());
        assertNull(handlers[1].getCatchType());
    }

    @Test
    public void rejectsTryCatchWithUnsupportedHandlerInstruction() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "unsupportedHandler", "(Ljava/lang/Throwable;)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(end);
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, null));
        method.maxStack = 1;
        method.maxLocals = 1;

        assertNull(InterpreterMethodEmitter.tryCompile(owner(), method));
    }

    @Test
    public void emitsReferenceNullBranchesAndLocalStoreGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "select", "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        LabelNode nullValue = new LabelNode();
        LabelNode nonnullValue = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, nullValue));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new JumpInsnNode(
                Opcodes.IFNONNULL, nonnullValue));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(nullValue);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(nonnullValue);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                47, 0, 0,
                50, 24, 0, 0, 0,
                47, 0, 0,
                48, 1, 0,
                47, 1, 0,
                51, 26, 0, 0, 0,
                46,
                49,
                46,
                49,
                47, 1, 0,
                49), compiled.getCode());
    }

    @Test
    public void rejectsUnsupportedObjectOperationWithoutMutation() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "readValue",
                "(LInterpreterFixture;)Ljava/lang/Object;", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "InterpreterFixture", "value", "Ljava/lang/Object;"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        AbstractInsnNode[] original = method.instructions.toArray();
        int originalAccess = method.access;

        assertNull(InterpreterMethodEmitter.tryCompile(owner(), method));
        assertArrayEquals(original, method.instructions.toArray());
        assertEquals(originalAccess, method.access);
    }

    @Test
    public void emitsRemainingLongArithmeticOpcodes() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "arithmetic", "(JJI)J", null, null);
        int[] opcodes = {
                Opcodes.LSUB, Opcodes.LMUL, Opcodes.LAND,
                Opcodes.LOR, Opcodes.LXOR, Opcodes.LDIV, Opcodes.LREM
        };
        for (int opcode : opcodes) {
            method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
            method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
            method.instructions.add(new InsnNode(opcode));
        }
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LSHR));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LUSHR));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxStack = 32;
        method.maxLocals = 5;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                31, 0, 0, 31, 2, 0, 34,
                31, 0, 0, 31, 2, 0, 35,
                31, 0, 0, 31, 2, 0, 36,
                31, 0, 0, 31, 2, 0, 37,
                31, 0, 0, 31, 2, 0, 38,
                31, 0, 0, 31, 2, 0, 44,
                31, 0, 0, 31, 2, 0, 45,
                31, 0, 0, 2, 4, 0, 40,
                31, 0, 0, 2, 4, 0, 41,
                43), compiled.getCode());
    }

    @Test
    public void emitsSubtractGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
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
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
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
    public void emitsMultiplyGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "multiply", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertEquals(4, InterpreterMethodEmitter.ISA_VERSION);
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                20,
                19), compiled.getCode());
    }

    @Test
    public void emitsBitwiseGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bitwise", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IOR));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 3;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                21,
                2, 0, 0,
                2, 1, 0,
                22,
                23,
                19), compiled.getCode());
    }

    @Test
    public void emitsShiftGolden() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "shift", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISHR));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 3;
        method.maxLocals = 2;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                2, 0, 0,
                2, 1, 0,
                24,
                2, 0, 0,
                2, 1, 0,
                25,
                23,
                2, 0, 0,
                2, 1, 0,
                26,
                23,
                19), compiled.getCode());
    }

    @Test
    public void emitsNegateDivideAndRemainder() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "arithmetic", "(III)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IREM));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 3;

        InterpreterMethodEmitter.CompiledMethod compiled =
                InterpreterMethodEmitter.tryCompile(owner(), method);

        assertNotNull(compiled);
        assertArrayEquals(bytes(
                2, 0, 0,
                27,
                2, 1, 0,
                28,
                2, 2, 0,
                29,
                19), compiled.getCode());
    }

    @Test
    public void leavesUnsupportedConversionForActiveCodegen() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "unsupportedConversion", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.I2L));
        method.instructions.add(new InsnNode(Opcodes.L2I));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 1;

        assertNull(InterpreterMethodEmitter.tryCompile(owner(), method));
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
