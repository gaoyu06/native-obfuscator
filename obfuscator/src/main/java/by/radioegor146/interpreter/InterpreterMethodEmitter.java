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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Minimal ASM-to-opcode lowering for the first integer-only interpreter slice.
 */
public final class InterpreterMethodEmitter {

    public static final int ISA_VERSION = 2;

    private static final int IPUSH = 0xa7;
    private static final int ILOAD = 0x31;
    private static final int ISTORE = 0xd4;
    private static final int IADD = 0x6b;
    private static final int ISUB = 0xe2;
    private static final int IFEQ = 0x19;
    private static final int IFNE = 0xc8;
    private static final int IFLT = 0x45;
    private static final int IFGE = 0x9a;
    private static final int IFGT = 0xf1;
    private static final int IFLE = 0x2d;
    private static final int IF_ICMPEQ = 0x74;
    private static final int IF_ICMPNE = 0xb6;
    private static final int IF_ICMPLT = 0x0f;
    private static final int IF_ICMPGE = 0x83;
    private static final int IF_ICMPGT = 0xdc;
    private static final int IF_ICMPLE = 0x52;
    private static final int GOTO = 0xae;
    private static final int IRETURN = 0x67;
    private static final int IMUL = 0x3c;
    private static final int IXOR = 0xf8;
    private static final int ISHL = 0x21;
    private static final int IUSHR = 0x95;
    private static final int IROTL = 0xca;

    private InterpreterMethodEmitter() {
    }

    public static CompiledMethod tryCompile(ClassNode owner, MethodNode method) {
        if ((owner.access & Opcodes.ACC_INTERFACE) != 0 ||
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
                case Opcodes.IADD:
                case Opcodes.ISUB:
                case Opcodes.IMUL:
                case Opcodes.IXOR:
                case Opcodes.ISHL:
                case Opcodes.IUSHR:
                case Opcodes.IRETURN:
                    return 1;
                default:
                    return -1;
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
        if (instruction instanceof MethodInsnNode) {
            return isIntegerRotateLeft((MethodInsnNode) instruction) ? 1 : -1;
        }
        return -1;
    }

    private static boolean emit(AbstractInsnNode instruction, Map<LabelNode, Integer> labelOffsets,
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
                case Opcodes.IADD:
                    code.write(IADD);
                    return true;
                case Opcodes.ISUB:
                    code.write(ISUB);
                    return true;
                case Opcodes.IMUL:
                    code.write(IMUL);
                    return true;
                case Opcodes.IXOR:
                    code.write(IXOR);
                    return true;
                case Opcodes.ISHL:
                    code.write(ISHL);
                    return true;
                case Opcodes.IUSHR:
                    code.write(IUSHR);
                    return true;
                case Opcodes.IRETURN:
                    code.write(IRETURN);
                    return true;
                default:
                    return false;
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
        if (instruction instanceof MethodInsnNode) {
            if (!isIntegerRotateLeft((MethodInsnNode) instruction)) {
                return false;
            }
            code.write(IROTL);
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

    private static boolean isIntegerRotateLeft(MethodInsnNode instruction) {
        return instruction.getOpcode() == Opcodes.INVOKESTATIC &&
                "java/lang/Integer".equals(instruction.owner) &&
                "rotateLeft".equals(instruction.name) &&
                "(II)I".equals(instruction.desc) &&
                !instruction.itf;
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
