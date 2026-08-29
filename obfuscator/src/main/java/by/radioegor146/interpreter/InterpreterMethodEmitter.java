package by.radioegor146.interpreter;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * ASM-to-opcode-stream lowering for the integer-only interpreter slice.
 */
public final class InterpreterMethodEmitter {

    public static final int ISA_VERSION = 2;

    public static final int IPUSH = 1;
    public static final int ILOAD = 2;
    public static final int ISTORE = 3;
    public static final int IADD = 4;
    public static final int ISUB = 5;
    public static final int IFEQ = 6;
    public static final int IFNE = 7;
    public static final int IFLT = 8;
    public static final int IFGE = 9;
    public static final int IFGT = 10;
    public static final int IFLE = 11;
    public static final int IF_ICMPEQ = 12;
    public static final int IF_ICMPNE = 13;
    public static final int IF_ICMPLT = 14;
    public static final int IF_ICMPGE = 15;
    public static final int IF_ICMPGT = 16;
    public static final int IF_ICMPLE = 17;
    public static final int GOTO = 18;
    public static final int IRETURN = 19;
    public static final int IMUL = 20;
    public static final int IAND = 21;
    public static final int IOR = 22;
    public static final int IXOR = 23;
    public static final int ISHL = 24;
    public static final int ISHR = 25;
    public static final int IUSHR = 26;
    public static final int INEG = 27;
    public static final int IDIV = 28;
    public static final int IREM = 29;

    private InterpreterMethodEmitter() {
    }

    public static CompiledMethod tryCompile(ClassNode owner, MethodNode method) {
        if ((owner.access & Opcodes.ACC_INTERFACE) != 0 ||
                (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 ||
                (method.access & Opcodes.ACC_STATIC) == 0 ||
                (method.access & Opcodes.ACC_SYNCHRONIZED) != 0 ||
                method.name.startsWith("<") ||
                Type.getReturnType(method.desc).getSort() != Type.INT ||
                !hasOnlyIntArguments(method.desc) ||
                (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) ||
                method.maxLocals > 0xffff ||
                method.maxStack > 0xffff - 2) {
            return null;
        }

        Map<LabelNode, Integer> labelOffsets = new IdentityHashMap<>();
        int codeLength = 0;
        boolean expandsIinc = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode) {
                labelOffsets.put((LabelNode) instruction, codeLength);
            }
            int size = instructionSize(instruction);
            if (size < 0) {
                return null;
            }
            codeLength += size;
            if (codeLength < 0) {
                return null;
            }
            expandsIinc |= instruction instanceof IincInsnNode;
        }

        ByteArrayOutputStream code = new ByteArrayOutputStream(codeLength);
        for (AbstractInsnNode instruction : method.instructions) {
            if (!emit(instruction, labelOffsets, code, codeLength)) {
                return null;
            }
        }

        int maxStack = method.maxStack + (expandsIinc ? 2 : 0);
        return new CompiledMethod(code.toByteArray(), maxStack, method.maxLocals);
    }

    private static boolean hasOnlyIntArguments(String descriptor) {
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            if (argument.getSort() != Type.INT) {
                return false;
            }
        }
        return true;
    }

    private static int instructionSize(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode < 0 || opcode == Opcodes.NOP) {
            return 0;
        }
        if (instruction instanceof InsnNode) {
            switch (opcode) {
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                    return 5;
                case Opcodes.IRETURN:
                    return 1;
                default:
                    return arithmeticOpcode(opcode) >= 0 ? 1 : -1;
            }
        }
        if (instruction instanceof IntInsnNode) {
            return opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ? 5 : -1;
        }
        if (instruction instanceof LdcInsnNode) {
            return ((LdcInsnNode) instruction).cst instanceof Integer ? 5 : -1;
        }
        if (instruction instanceof VarInsnNode) {
            VarInsnNode variable = (VarInsnNode) instruction;
            return (opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE) &&
                    variable.var >= 0 && variable.var <= 0xffff ? 3 : -1;
        }
        if (instruction instanceof IincInsnNode) {
            int variable = ((IincInsnNode) instruction).var;
            return variable >= 0 && variable <= 0xffff ? 12 : -1;
        }
        if (instruction instanceof JumpInsnNode) {
            return branchOpcode(opcode) >= 0 ? 5 : -1;
        }
        return -1;
    }

    private static boolean emit(AbstractInsnNode instruction,
                                Map<LabelNode, Integer> labelOffsets,
                                ByteArrayOutputStream code, int codeLength) {
        int opcode = instruction.getOpcode();
        if (opcode < 0 || opcode == Opcodes.NOP) {
            return true;
        }
        if (instruction instanceof InsnNode) {
            switch (opcode) {
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                    emitIntConstant(code, opcode - Opcodes.ICONST_0);
                    return true;
                case Opcodes.IRETURN:
                    code.write(IRETURN);
                    return true;
                default:
                    int interpretedOpcode = arithmeticOpcode(opcode);
                    if (interpretedOpcode < 0) {
                        return false;
                    }
                    code.write(interpretedOpcode);
                    return true;
            }
        }
        if (instruction instanceof IntInsnNode) {
            emitIntConstant(code, ((IntInsnNode) instruction).operand);
            return true;
        }
        if (instruction instanceof LdcInsnNode) {
            emitIntConstant(code, (Integer) ((LdcInsnNode) instruction).cst);
            return true;
        }
        if (instruction instanceof VarInsnNode) {
            VarInsnNode variable = (VarInsnNode) instruction;
            code.write(opcode == Opcodes.ILOAD ? ILOAD : ISTORE);
            writeU16(code, variable.var);
            return true;
        }
        if (instruction instanceof IincInsnNode) {
            IincInsnNode increment = (IincInsnNode) instruction;
            code.write(ILOAD);
            writeU16(code, increment.var);
            emitIntConstant(code, increment.incr);
            code.write(IADD);
            code.write(ISTORE);
            writeU16(code, increment.var);
            return true;
        }
        if (instruction instanceof JumpInsnNode) {
            JumpInsnNode jump = (JumpInsnNode) instruction;
            Integer target = labelOffsets.get(jump.label);
            if (target == null || target < 0 || target >= codeLength) {
                return false;
            }
            code.write(branchOpcode(opcode));
            writeI32(code, target);
            return true;
        }
        return false;
    }

    private static void emitIntConstant(ByteArrayOutputStream code, int value) {
        code.write(IPUSH);
        writeI32(code, value);
    }

    private static int branchOpcode(int asmOpcode) {
        switch (asmOpcode) {
            case Opcodes.IFEQ:
                return IFEQ;
            case Opcodes.IFNE:
                return IFNE;
            case Opcodes.IFLT:
                return IFLT;
            case Opcodes.IFGE:
                return IFGE;
            case Opcodes.IFGT:
                return IFGT;
            case Opcodes.IFLE:
                return IFLE;
            case Opcodes.IF_ICMPEQ:
                return IF_ICMPEQ;
            case Opcodes.IF_ICMPNE:
                return IF_ICMPNE;
            case Opcodes.IF_ICMPLT:
                return IF_ICMPLT;
            case Opcodes.IF_ICMPGE:
                return IF_ICMPGE;
            case Opcodes.IF_ICMPGT:
                return IF_ICMPGT;
            case Opcodes.IF_ICMPLE:
                return IF_ICMPLE;
            case Opcodes.GOTO:
                return GOTO;
            default:
                return -1;
        }
    }

    private static int arithmeticOpcode(int asmOpcode) {
        switch (asmOpcode) {
            case Opcodes.IADD:
                return IADD;
            case Opcodes.ISUB:
                return ISUB;
            case Opcodes.IMUL:
                return IMUL;
            case Opcodes.IAND:
                return IAND;
            case Opcodes.IOR:
                return IOR;
            case Opcodes.IXOR:
                return IXOR;
            case Opcodes.ISHL:
                return ISHL;
            case Opcodes.ISHR:
                return ISHR;
            case Opcodes.IUSHR:
                return IUSHR;
            case Opcodes.INEG:
                return INEG;
            case Opcodes.IDIV:
                return IDIV;
            case Opcodes.IREM:
                return IREM;
            default:
                return -1;
        }
    }

    private static void writeU16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeI32(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    public static final class CompiledMethod {
        private final byte[] code;
        private final int maxStack;
        private final int maxLocals;

        private CompiledMethod(byte[] code, int maxStack, int maxLocals) {
            this.code = code;
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
        }

        public byte[] getCode() {
            return code.clone();
        }

        public int getMaxStack() {
            return maxStack;
        }

        public int getMaxLocals() {
            return maxLocals;
        }
    }
}
