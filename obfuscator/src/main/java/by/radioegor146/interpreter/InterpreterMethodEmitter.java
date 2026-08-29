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
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ASM-to-opcode-stream lowering for the integer, long, and reference
 * interpreter slices.
 */
public final class InterpreterMethodEmitter {

    public static final int ISA_VERSION = 4;

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
    public static final int LPUSH = 30;
    public static final int LLOAD = 31;
    public static final int LSTORE = 32;
    public static final int LADD = 33;
    public static final int LSUB = 34;
    public static final int LMUL = 35;
    public static final int LAND = 36;
    public static final int LOR = 37;
    public static final int LXOR = 38;
    public static final int LSHL = 39;
    public static final int LSHR = 40;
    public static final int LUSHR = 41;
    public static final int LNEG = 42;
    public static final int LRETURN = 43;
    public static final int LDIV = 44;
    public static final int LREM = 45;
    public static final int ACONST_NULL = 46;
    public static final int ALOAD = 47;
    public static final int ASTORE = 48;
    public static final int ARETURN = 49;
    public static final int IFNULL = 50;
    public static final int IFNONNULL = 51;
    public static final int ATHROW = 52;
    public static final int DUP = 53;
    public static final int NEW = 54;
    public static final int INVOKESPECIAL = 55;

    private InterpreterMethodEmitter() {
    }

    public static CompiledMethod tryCompile(ClassNode owner, MethodNode method) {
        if ((owner.access & Opcodes.ACC_INTERFACE) != 0 ||
                (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 ||
                (method.access & Opcodes.ACC_STATIC) == 0 ||
                (method.access & Opcodes.ACC_SYNCHRONIZED) != 0 ||
                method.name.startsWith("<") ||
                !isSupportedType(Type.getReturnType(method.desc)) ||
                !hasOnlySupportedArguments(method.desc) ||
                method.maxLocals > 0xffff ||
                method.maxStack > 0xffff - 2) {
            return null;
        }

        Map<LabelNode, Integer> labelOffsets = new IdentityHashMap<>();
        int codeLength = 0;
        boolean expandsIinc = false;
        boolean provableReferenceTop = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode) {
                labelOffsets.put((LabelNode) instruction, codeLength);
            }
            int size = instructionSize(instruction, provableReferenceTop);
            if (size < 0) {
                return null;
            }
            codeLength += size;
            if (codeLength < 0) {
                return null;
            }
            expandsIinc |= instruction instanceof IincInsnNode;
            provableReferenceTop = updateProvableReferenceTop(
                    instruction, provableReferenceTop);
        }

        SideTables sideTables = new SideTables();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode) {
                if (sideTables.addClass(
                        ((TypeInsnNode) instruction).desc) < 0) {
                    return null;
                }
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (sideTables.addConstructor(
                        invocation.owner, invocation.desc) < 0) {
                    return null;
                }
            }
        }

        ByteArrayOutputStream code = new ByteArrayOutputStream(codeLength);
        for (AbstractInsnNode instruction : method.instructions) {
            if (!emit(instruction, labelOffsets, code, codeLength,
                    sideTables)) {
                return null;
            }
        }

        List<ExceptionHandler> exceptionHandlers = new ArrayList<>();
        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
                Integer startPc = labelOffsets.get(tryCatch.start);
                Integer endPc = labelOffsets.get(tryCatch.end);
                Integer handlerPc = labelOffsets.get(tryCatch.handler);
                if (startPc == null || endPc == null || handlerPc == null ||
                        startPc < 0 || startPc >= endPc ||
                        endPc > codeLength || handlerPc < 0 ||
                        handlerPc >= codeLength ||
                        (tryCatch.type != null && tryCatch.type.isEmpty())) {
                    return null;
                }
                exceptionHandlers.add(new ExceptionHandler(
                        startPc, endPc, handlerPc, tryCatch.type));
            }
        }

        int maxStack = method.maxStack + (expandsIinc ? 2 : 0);
        return new CompiledMethod(code.toByteArray(), maxStack,
                method.maxLocals, exceptionHandlers.toArray(
                new ExceptionHandler[exceptionHandlers.size()]),
                sideTables.classes.toArray(new String[sideTables.classes.size()]),
                sideTables.constructors.toArray(
                        new ConstructorReference[
                                sideTables.constructors.size()]));
    }

    private static boolean hasOnlySupportedArguments(String descriptor) {
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            if (!isSupportedType(argument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSupportedType(Type type) {
        int sort = type.getSort();
        return sort == Type.INT || sort == Type.LONG ||
                sort == Type.OBJECT || sort == Type.ARRAY;
    }

    private static int instructionSize(AbstractInsnNode instruction,
                                       boolean provableReferenceTop) {
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
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                    return 9;
                case Opcodes.ACONST_NULL:
                case Opcodes.IRETURN:
                case Opcodes.LRETURN:
                case Opcodes.ARETURN:
                case Opcodes.ATHROW:
                    return 1;
                case Opcodes.DUP:
                    return provableReferenceTop ? 1 : -1;
                default:
                    return arithmeticOpcode(opcode) >= 0 ? 1 : -1;
            }
        }
        if (instruction instanceof IntInsnNode) {
            return opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ? 5 : -1;
        }
        if (instruction instanceof LdcInsnNode) {
            Object constant = ((LdcInsnNode) instruction).cst;
            if (constant instanceof Integer) {
                return 5;
            }
            return constant instanceof Long ? 9 : -1;
        }
        if (instruction instanceof VarInsnNode) {
            VarInsnNode variable = (VarInsnNode) instruction;
            boolean supported = opcode == Opcodes.ILOAD ||
                    opcode == Opcodes.ISTORE ||
                    opcode == Opcodes.LLOAD ||
                    opcode == Opcodes.LSTORE ||
                    opcode == Opcodes.ALOAD ||
                    opcode == Opcodes.ASTORE;
            return supported && variable.var >= 0 &&
                    variable.var <= 0xffff ? 3 : -1;
        }
        if (instruction instanceof IincInsnNode) {
            int variable = ((IincInsnNode) instruction).var;
            return variable >= 0 && variable <= 0xffff ? 12 : -1;
        }
        if (instruction instanceof JumpInsnNode) {
            return branchOpcode(opcode) >= 0 ? 5 : -1;
        }
        if (instruction instanceof TypeInsnNode) {
            TypeInsnNode type = (TypeInsnNode) instruction;
            return opcode == Opcodes.NEW && type.desc != null &&
                    !type.desc.isEmpty() ? 3 : -1;
        }
        if (instruction instanceof MethodInsnNode) {
            return isSupportedConstructorInvocation(
                    (MethodInsnNode) instruction) ? 3 : -1;
        }
        return -1;
    }

    private static boolean emit(AbstractInsnNode instruction,
                                Map<LabelNode, Integer> labelOffsets,
                                ByteArrayOutputStream code, int codeLength,
                                SideTables sideTables) {
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
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                    emitLongConstant(code, opcode - Opcodes.LCONST_0);
                    return true;
                case Opcodes.ACONST_NULL:
                    code.write(ACONST_NULL);
                    return true;
                case Opcodes.IRETURN:
                    code.write(IRETURN);
                    return true;
                case Opcodes.LRETURN:
                    code.write(LRETURN);
                    return true;
                case Opcodes.ARETURN:
                    code.write(ARETURN);
                    return true;
                case Opcodes.ATHROW:
                    code.write(ATHROW);
                    return true;
                case Opcodes.DUP:
                    code.write(DUP);
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
            Object constant = ((LdcInsnNode) instruction).cst;
            if (constant instanceof Integer) {
                emitIntConstant(code, (Integer) constant);
            } else {
                emitLongConstant(code, (Long) constant);
            }
            return true;
        }
        if (instruction instanceof VarInsnNode) {
            VarInsnNode variable = (VarInsnNode) instruction;
            code.write(variableOpcode(opcode));
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
        if (instruction instanceof TypeInsnNode) {
            int index = sideTables.classIndexes.get(
                    ((TypeInsnNode) instruction).desc);
            code.write(NEW);
            writeU16(code, index);
            return true;
        }
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            Integer index = sideTables.constructorIndexes.get(
                    constructorKey(invocation.owner, invocation.desc));
            if (index == null) {
                return false;
            }
            code.write(INVOKESPECIAL);
            writeU16(code, index);
            return true;
        }
        return false;
    }

    private static boolean updateProvableReferenceTop(
            AbstractInsnNode instruction, boolean previous) {
        if (instruction instanceof LabelNode) {
            return false;
        }
        int opcode = instruction.getOpcode();
        if (opcode < 0 || opcode == Opcodes.NOP) {
            return previous;
        }
        return opcode == Opcodes.NEW || opcode == Opcodes.ALOAD ||
                opcode == Opcodes.ACONST_NULL ||
                (opcode == Opcodes.DUP && previous);
    }

    private static boolean isSupportedConstructorInvocation(
            MethodInsnNode invocation) {
        if (invocation.getOpcode() != Opcodes.INVOKESPECIAL ||
                invocation.itf || !"<init>".equals(invocation.name) ||
                invocation.owner == null || invocation.owner.isEmpty()) {
            return false;
        }
        try {
            return Type.getReturnType(invocation.desc).getSort() == Type.VOID &&
                    hasOnlySupportedArguments(invocation.desc);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String constructorKey(String owner, String descriptor) {
        return owner + '\0' + descriptor;
    }

    private static void emitIntConstant(ByteArrayOutputStream code, int value) {
        code.write(IPUSH);
        writeI32(code, value);
    }

    private static void emitLongConstant(ByteArrayOutputStream code, long value) {
        code.write(LPUSH);
        writeI64(code, value);
    }

    private static int variableOpcode(int asmOpcode) {
        switch (asmOpcode) {
            case Opcodes.ILOAD:
                return ILOAD;
            case Opcodes.ISTORE:
                return ISTORE;
            case Opcodes.LLOAD:
                return LLOAD;
            case Opcodes.LSTORE:
                return LSTORE;
            case Opcodes.ALOAD:
                return ALOAD;
            case Opcodes.ASTORE:
                return ASTORE;
            default:
                return -1;
        }
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
            case Opcodes.IFNULL:
                return IFNULL;
            case Opcodes.IFNONNULL:
                return IFNONNULL;
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
            case Opcodes.LADD:
                return LADD;
            case Opcodes.LSUB:
                return LSUB;
            case Opcodes.LMUL:
                return LMUL;
            case Opcodes.LAND:
                return LAND;
            case Opcodes.LOR:
                return LOR;
            case Opcodes.LXOR:
                return LXOR;
            case Opcodes.LSHL:
                return LSHL;
            case Opcodes.LSHR:
                return LSHR;
            case Opcodes.LUSHR:
                return LUSHR;
            case Opcodes.LNEG:
                return LNEG;
            case Opcodes.LDIV:
                return LDIV;
            case Opcodes.LREM:
                return LREM;
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

    private static void writeI64(ByteArrayOutputStream output, long value) {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
        output.write((int) ((value >>> 32) & 0xff));
        output.write((int) ((value >>> 40) & 0xff));
        output.write((int) ((value >>> 48) & 0xff));
        output.write((int) ((value >>> 56) & 0xff));
    }

    private static final class SideTables {
        private final List<String> classes = new ArrayList<>();
        private final Map<String, Integer> classIndexes =
                new LinkedHashMap<>();
        private final List<ConstructorReference> constructors =
                new ArrayList<>();
        private final Map<String, Integer> constructorIndexes =
                new LinkedHashMap<>();

        private int addClass(String className) {
            Integer existing = classIndexes.get(className);
            if (existing != null) {
                return existing;
            }
            if (classes.size() >= 0x10000) {
                return -1;
            }
            int index = classes.size();
            classes.add(className);
            classIndexes.put(className, index);
            return index;
        }

        private int addConstructor(String owner, String descriptor) {
            String key = constructorKey(owner, descriptor);
            Integer existing = constructorIndexes.get(key);
            if (existing != null) {
                return existing;
            }
            if (constructors.size() >= 0x10000) {
                return -1;
            }
            int classIndex = addClass(owner);
            if (classIndex < 0) {
                return -1;
            }
            Type[] arguments = Type.getArgumentTypes(descriptor);
            int index = constructors.size();
            constructors.add(new ConstructorReference(
                    classIndex, descriptor, arguments));
            constructorIndexes.put(key, index);
            return index;
        }
    }

    public static final class CompiledMethod {
        private final byte[] code;
        private final int maxStack;
        private final int maxLocals;
        private final ExceptionHandler[] exceptionHandlers;
        private final String[] classes;
        private final ConstructorReference[] constructors;

        private CompiledMethod(byte[] code, int maxStack, int maxLocals,
                               ExceptionHandler[] exceptionHandlers,
                               String[] classes,
                               ConstructorReference[] constructors) {
            this.code = code;
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.exceptionHandlers = exceptionHandlers;
            this.classes = classes;
            this.constructors = constructors;
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

        public ExceptionHandler[] getExceptionHandlers() {
            return exceptionHandlers.clone();
        }

        public String[] getClasses() {
            return classes.clone();
        }

        public ConstructorReference[] getConstructors() {
            return constructors.clone();
        }
    }

    public static final class ConstructorReference {
        private final int classIndex;
        private final String descriptor;
        private final Type[] argumentTypes;
        private final int argumentSlots;

        private ConstructorReference(int classIndex, String descriptor,
                                     Type[] argumentTypes) {
            this.classIndex = classIndex;
            this.descriptor = descriptor;
            this.argumentTypes = argumentTypes.clone();
            int slots = 0;
            for (Type argument : argumentTypes) {
                slots += argument.getSize();
            }
            this.argumentSlots = slots;
        }

        public int getClassIndex() {
            return classIndex;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public Type[] getArgumentTypes() {
            return argumentTypes.clone();
        }

        public int getArgumentSlots() {
            return argumentSlots;
        }
    }

    public static final class ExceptionHandler {
        private final int startPc;
        private final int endPc;
        private final int handlerPc;
        private final String catchType;

        private ExceptionHandler(int startPc, int endPc, int handlerPc,
                                 String catchType) {
            this.startPc = startPc;
            this.endPc = endPc;
            this.handlerPc = handlerPc;
            this.catchType = catchType;
        }

        public int getStartPc() {
            return startPc;
        }

        public int getEndPc() {
            return endPc;
        }

        public int getHandlerPc() {
            return handlerPc;
        }

        public String getCatchType() {
            return catchType;
        }
    }
}
