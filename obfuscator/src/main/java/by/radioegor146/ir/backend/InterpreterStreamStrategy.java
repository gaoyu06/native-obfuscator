package by.radioegor146.ir.backend;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;
import by.radioegor146.ir.UnsupportedIrConstructException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes the pure integer IR slice to the shared native evaluator ISA.
 */
public final class InterpreterStreamStrategy implements MethodLoweringStrategy {
    private static final int MAGIC_N = 0x4e;
    private static final int MAGIC_J = 0x4a;
    private static final int MAGIC_E = 0x45;
    private static final int FORMAT_VERSION = 1;

    private static final int OP_CONST_I32 = 0x01;
    private static final int OP_MOVE = 0x02;
    private static final int OP_IADD = 0x10;
    private static final int OP_ISUB = 0x11;
    private static final int OP_IMUL = 0x12;
    private static final int OP_IAND = 0x13;
    private static final int OP_IOR = 0x14;
    private static final int OP_IXOR = 0x15;
    private static final int OP_ISHL = 0x16;
    private static final int OP_ISHR = 0x17;
    private static final int OP_IUSHR = 0x18;
    private static final int OP_JUMP = 0x20;
    private static final int OP_BRANCH = 0x21;
    private static final int OP_RETURN_I32 = 0x22;
    private static final int OP_LLOAD = 0x23;
    private static final int OP_LSTORE = 0x24;
    private static final int OP_LADD = 0x25;
    private static final int OP_LSUB = 0x26;
    private static final int OP_LMUL = 0x27;
    private static final int OP_LRETURN = 0x28;
    private static final int OP_I2L = 0x29;
    private static final int OP_L2I = 0x2a;
    // 0x2b and 0x2c are reserved for future LDIV/LREM lowering.
    private static final int OP_LAND = 0x2d;
    private static final int OP_LOR = 0x2e;
    private static final int OP_LXOR = 0x2f;
    private static final int OP_LSHL = 0x30;
    private static final int OP_LSHR = 0x31;
    private static final int OP_LUSHR = 0x32;

    private static final int ZERO_REGISTER = 0xffff;
    private static final int MAX_REGISTER_COUNT = 0xffff;

    @Override
    public boolean supports(IrMethod method) {
        if (method == null) {
            return false;
        }
        try {
            validate(method);
            return true;
        } catch (UnsupportedIrConstructException ignored) {
            return false;
        }
    }

    @Override
    public LoweredMethod lower(IrMethod method, LoweringContext context) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(context, "context");
        validate(method);
        byte[] data = new Serializer(method).serialize();
        return LoweredMethod.evaluator(emitTrampoline(method, data), data);
    }

    private void validate(IrMethod method) {
        if (!method.isStaticMethod()) {
            throw unsupported("Evaluator lowering currently supports static methods only", -1);
        }
        if (method.getReturnType() != IrType.I32
                && method.getReturnType() != IrType.I64) {
            throw unsupported("Evaluator lowering requires an i32 or i64 method return", -1);
        }
        for (int i = 0; i < method.getParameters().size(); i++) {
            IrValue parameter = method.getParameters().get(i);
            if (!isEvaluatorValueType(parameter.getType()) || parameter.getId() != i) {
                throw unsupported(
                        "Evaluator arguments must be contiguous i32/i64 IR parameters", -1);
            }
        }

        int maximumRegister = -1;
        for (IrValue parameter : method.getParameters()) {
            maximumRegister = Math.max(maximumRegister,
                    checkedValue(parameter, parameter.getType(), -1));
        }
        for (IrBlock block : method.getBlocks()) {
            if (!block.getExceptionEdges().isEmpty()) {
                throw unsupported(
                        "Evaluator lowering does not yet support exception edges", -1);
            }
            for (IrPhi phi : block.getPhis()) {
                maximumRegister = Math.max(maximumRegister,
                        checkedEvaluatorValue(phi.getResult(), -1));
                for (IrValue incoming : phi.getIncoming().values()) {
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(incoming, phi.getResult().getType(), -1));
                }
            }
            for (IrInstruction instruction : block.getInstructions()) {
                int offset = instruction.getBytecodeOffset();
                if (instruction instanceof IrNodes.Const) {
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(instruction.getResult(), IrType.I32, offset));
                } else if (instruction instanceof IrNodes.Binary) {
                    IrNodes.Binary binary = (IrNodes.Binary) instruction;
                    if (!isSupportedBinaryOperation(binary.getOperation())) {
                        throw unsupported("Unsupported evaluator binary operation "
                                + binary.getOperation(), offset);
                    }
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getResult(), IrType.I32, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getLeft(), IrType.I32, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getRight(), IrType.I32, offset));
                } else if (instruction instanceof IrNodes.LongBinary) {
                    IrNodes.LongBinary binary = (IrNodes.LongBinary) instruction;
                    if (!isSupportedLongBinaryOperation(binary.getOperation())) {
                        throw unsupported("Unsupported evaluator long binary operation "
                                + binary.getOperation(), offset);
                    }
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getResult(), IrType.I64, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getLeft(), IrType.I64, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(binary.getRight(), IrType.I64, offset));
                } else if (instruction instanceof IrNodes.LongShift) {
                    IrNodes.LongShift shift = (IrNodes.LongShift) instruction;
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(shift.getResult(), IrType.I64, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(shift.getValue(), IrType.I64, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(shift.getCount(), IrType.I32, offset));
                } else if (instruction instanceof IrNodes.Conversion) {
                    IrNodes.Conversion conversion = (IrNodes.Conversion) instruction;
                    if (conversion.getOperation() != IrNodes.Conversion.Operation.I2L
                            && conversion.getOperation()
                            != IrNodes.Conversion.Operation.L2I) {
                        throw unsupported("Unsupported evaluator conversion "
                                + conversion.getOperation(), offset);
                    }
                    IrType operandType = conversion.getOperation()
                            == IrNodes.Conversion.Operation.I2L ? IrType.I32 : IrType.I64;
                    IrType resultType = conversion.getOperation()
                            == IrNodes.Conversion.Operation.I2L ? IrType.I64 : IrType.I32;
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(conversion.getOperand(), operandType, offset));
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(conversion.getResult(), resultType, offset));
                } else {
                    throw unsupported("Unsupported evaluator instruction "
                            + instruction.getClass().getSimpleName(), offset);
                }
            }
            IrTerminator terminator = block.getTerminator();
            if (terminator == null) {
                throw unsupported("IR block has no terminator", -1);
            }
            int offset = terminator.getBytecodeOffset();
            if (terminator instanceof IrNodes.Goto) {
                // No value operands.
            } else if (terminator instanceof IrNodes.Branch) {
                IrNodes.Branch branch = (IrNodes.Branch) terminator;
                maximumRegister = Math.max(maximumRegister,
                        checkedValue(branch.getLeft(), IrType.I32, offset));
                if (branch.getRight() != null) {
                    maximumRegister = Math.max(maximumRegister,
                            checkedValue(branch.getRight(), IrType.I32, offset));
                }
            } else if (terminator instanceof IrNodes.Return) {
                IrValue value = ((IrNodes.Return) terminator).getValue();
                if (value == null) {
                    throw unsupported("Evaluator lowering requires IRETURN or LRETURN", offset);
                }
                maximumRegister = Math.max(maximumRegister,
                        checkedValue(value, method.getReturnType(), offset));
            } else {
                throw unsupported("Unsupported evaluator terminator "
                        + terminator.getClass().getSimpleName(), offset);
            }
        }

        int temporaryCount = 0;
        for (IrBlock block : method.getBlocks()) {
            temporaryCount = Math.max(temporaryCount, block.getPhis().size());
        }
        long registerCount = (long) maximumRegister + 1L + temporaryCount
                + (method.getReturnType() == IrType.I64 ? 1L : 0L);
        if (registerCount <= 0 || registerCount > MAX_REGISTER_COUNT) {
            throw unsupported("Evaluator register count exceeds the u16 ISA limit", -1);
        }
        if (method.getParameters().size() > MAX_REGISTER_COUNT) {
            throw unsupported("Evaluator argument count exceeds the u16 ISA limit", -1);
        }
    }

    private boolean isSupportedLongBinaryOperation(
            IrNodes.LongBinary.Operation operation) {
        switch (operation) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case AND:
            case OR:
            case XOR:
                return true;
            default:
                return false;
        }
    }

    private boolean isSupportedBinaryOperation(IrNodes.Binary.Operation operation) {
        switch (operation) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case AND:
            case OR:
            case XOR:
            case SHL:
            case SHR:
            case USHR:
                return true;
            default:
                return false;
        }
    }

    private boolean isEvaluatorValueType(IrType type) {
        return type == IrType.I32 || type == IrType.I64;
    }

    private int checkedEvaluatorValue(IrValue value, int offset) {
        if (value == null || !isEvaluatorValueType(value.getType())) {
            throw unsupported("Evaluator operands must be i32 or i64 values", offset);
        }
        return checkedRegisterId(value, offset);
    }

    private int checkedValue(IrValue value, IrType type, int offset) {
        if (value == null || value.getType() != type) {
            throw unsupported("Evaluator operand must be " + type, offset);
        }
        return checkedRegisterId(value, offset);
    }

    private int checkedRegisterId(IrValue value, int offset) {
        if (value.getId() < 0 || value.getId() >= ZERO_REGISTER) {
            throw unsupported("Evaluator register id exceeds the u16 ISA limit", offset);
        }
        return value.getId();
    }

    private String emitTrampoline(IrMethod method, byte[] data) {
        StringBuilder out = new StringBuilder();
        out.append("    // IR evaluator data: ")
                .append(method.getOwner()).append('.').append(method.getName())
                .append(method.getDescriptor()).append("\n");
        out.append("    static const std::uint8_t ir_method_data[] = {\n");
        for (int i = 0; i < data.length; i++) {
            if (i % 16 == 0) {
                out.append("        ");
            }
            out.append(data[i] & 0xff);
            if (i + 1 != data.length) {
                out.append(", ");
            }
            if (i % 16 == 15 || i + 1 == data.length) {
                out.append("\n");
            }
        }
        out.append("    };\n");
        String evaluator = method.getReturnType() == IrType.I64
                ? "evaluate_i64" : "evaluate_i32";
        if (method.getParameters().isEmpty()) {
            out.append("    return native_jvm::ir_eval::").append(evaluator)
                    .append("(env, ir_method_data, ")
                    .append("sizeof(ir_method_data), nullptr, 0);\n");
        } else {
            out.append("    const jlong ir_method_args[] = { ");
            for (int i = 0; i < method.getParameters().size(); i++) {
                if (i != 0) {
                    out.append(", ");
                }
                IrValue parameter = method.getParameters().get(i);
                if (parameter.getType() == IrType.I32) {
                    out.append("static_cast<jlong>(")
                            .append(parameter.getCppParameterName()).append(')');
                } else {
                    out.append(parameter.getCppParameterName());
                }
            }
            out.append(" };\n");
            out.append("    return native_jvm::ir_eval::").append(evaluator)
                    .append("(env, ir_method_data, ")
                    .append("sizeof(ir_method_data), ir_method_args, ")
                    .append(method.getParameters().size()).append(");\n");
        }
        return out.toString();
    }

    private static UnsupportedIrConstructException unsupported(String message, int offset) {
        return new UnsupportedIrConstructException(message, offset, -1);
    }

    private static final class Serializer {
        private final IrMethod method;
        private final ByteWriter writer = new ByteWriter();
        private final Map<IrBlock, Label> blockLabels = new IdentityHashMap<>();
        private final List<Patch> patches = new ArrayList<>();
        private final List<Edge> conditionalEdges = new ArrayList<>();
        private final int baseRegisterCount;
        private final int registerCount;
        private final int longReturnRegister;

        private Serializer(IrMethod method) {
            this.method = method;
            for (IrBlock block : method.getBlocks()) {
                blockLabels.put(block, new Label());
            }
            int maximumRegister = -1;
            int maximumPhiCount = 0;
            for (IrValue parameter : method.getParameters()) {
                maximumRegister = Math.max(maximumRegister, parameter.getId());
            }
            for (IrBlock block : method.getBlocks()) {
                maximumPhiCount = Math.max(maximumPhiCount, block.getPhis().size());
                for (IrPhi phi : block.getPhis()) {
                    maximumRegister = Math.max(maximumRegister, phi.getResult().getId());
                }
                for (IrInstruction instruction : block.getInstructions()) {
                    if (instruction.getResult() != null) {
                        maximumRegister = Math.max(maximumRegister,
                                instruction.getResult().getId());
                    }
                }
            }
            baseRegisterCount = maximumRegister + 1;
            longReturnRegister = method.getReturnType() == IrType.I64
                    ? baseRegisterCount + maximumPhiCount : -1;
            registerCount = baseRegisterCount + maximumPhiCount
                    + (longReturnRegister >= 0 ? 1 : 0);
        }

        private byte[] serialize() {
            writer.u8(MAGIC_N);
            writer.u8(MAGIC_J);
            writer.u8(MAGIC_E);
            writer.u8(FORMAT_VERSION);
            writer.u16(registerCount);
            writer.u16(method.getParameters().size());

            for (int i = 0; i < method.getParameters().size(); i++) {
                IrValue parameter = method.getParameters().get(i);
                if (parameter.getType() == IrType.I64) {
                    writer.u8(OP_LLOAD);
                    writer.u16(parameter.getId());
                    writer.u16(i);
                }
            }
            for (IrBlock block : method.getBlocks()) {
                mark(blockLabels.get(block));
                for (IrInstruction instruction : block.getInstructions()) {
                    emitInstruction(instruction);
                }
                emitTerminator(block, block.getTerminator());
            }
            for (Edge edge : conditionalEdges) {
                mark(edge.label);
                emitPhiCopies(edge.predecessor, edge.target);
                emitJump(requiredBlockLabel(edge.target));
            }
            resolvePatches();
            return writer.toByteArray();
        }

        private void emitInstruction(IrInstruction instruction) {
            if (instruction instanceof IrNodes.Const) {
                IrNodes.Const constant = (IrNodes.Const) instruction;
                writer.u8(OP_CONST_I32);
                writer.u16(constant.getResult().getId());
                writer.i32(constant.getValue());
                return;
            }
            if (instruction instanceof IrNodes.Binary) {
                emitIntBinary((IrNodes.Binary) instruction);
                return;
            }
            if (instruction instanceof IrNodes.LongBinary) {
                emitLongBinary((IrNodes.LongBinary) instruction);
                return;
            }
            if (instruction instanceof IrNodes.LongShift) {
                emitLongShift((IrNodes.LongShift) instruction);
                return;
            }
            IrNodes.Conversion conversion = (IrNodes.Conversion) instruction;
            writer.u8(conversion.getOperation() == IrNodes.Conversion.Operation.I2L
                    ? OP_I2L : OP_L2I);
            writer.u16(conversion.getResult().getId());
            writer.u16(conversion.getOperand().getId());
        }

        private void emitIntBinary(IrNodes.Binary binary) {
            switch (binary.getOperation()) {
                case ADD:
                    writer.u8(OP_IADD);
                    break;
                case SUBTRACT:
                    writer.u8(OP_ISUB);
                    break;
                case MULTIPLY:
                    writer.u8(OP_IMUL);
                    break;
                case AND:
                    writer.u8(OP_IAND);
                    break;
                case OR:
                    writer.u8(OP_IOR);
                    break;
                case XOR:
                    writer.u8(OP_IXOR);
                    break;
                case SHL:
                    writer.u8(OP_ISHL);
                    break;
                case SHR:
                    writer.u8(OP_ISHR);
                    break;
                case USHR:
                    writer.u8(OP_IUSHR);
                    break;
                default:
                    throw new IllegalStateException("Validated binary operation changed");
            }
            writer.u16(binary.getResult().getId());
            writer.u16(binary.getLeft().getId());
            writer.u16(binary.getRight().getId());
        }

        private void emitLongBinary(IrNodes.LongBinary binary) {
            switch (binary.getOperation()) {
                case ADD:
                    writer.u8(OP_LADD);
                    break;
                case SUBTRACT:
                    writer.u8(OP_LSUB);
                    break;
                case MULTIPLY:
                    writer.u8(OP_LMUL);
                    break;
                case AND:
                    writer.u8(OP_LAND);
                    break;
                case OR:
                    writer.u8(OP_LOR);
                    break;
                case XOR:
                    writer.u8(OP_LXOR);
                    break;
                default:
                    throw new IllegalStateException("Validated long operation changed");
            }
            writer.u16(binary.getResult().getId());
            writer.u16(binary.getLeft().getId());
            writer.u16(binary.getRight().getId());
        }

        private void emitLongShift(IrNodes.LongShift shift) {
            switch (shift.getOperation()) {
                case SHL:
                    writer.u8(OP_LSHL);
                    break;
                case SHR:
                    writer.u8(OP_LSHR);
                    break;
                case USHR:
                    writer.u8(OP_LUSHR);
                    break;
                default:
                    throw new IllegalStateException(
                            "Validated long shift operation changed");
            }
            writer.u16(shift.getResult().getId());
            writer.u16(shift.getValue().getId());
            writer.u16(shift.getCount().getId());
        }

        private void emitTerminator(IrBlock predecessor, IrTerminator terminator) {
            if (terminator instanceof IrNodes.Goto) {
                IrBlock target = ((IrNodes.Goto) terminator).getTarget();
                emitPhiCopies(predecessor, target);
                emitJump(requiredBlockLabel(target));
                return;
            }
            if (terminator instanceof IrNodes.Branch) {
                IrNodes.Branch branch = (IrNodes.Branch) terminator;
                Edge trueEdge = new Edge(predecessor, branch.getTrueTarget());
                Edge falseEdge = new Edge(predecessor, branch.getFalseTarget());
                conditionalEdges.add(trueEdge);
                conditionalEdges.add(falseEdge);

                writer.u8(OP_BRANCH);
                writer.u8(conditionCode(branch.getCondition()));
                writer.u16(branch.getLeft().getId());
                writer.u16(branch.getRight() == null
                        ? ZERO_REGISTER : branch.getRight().getId());
                emitTarget(trueEdge.label);
                emitTarget(falseEdge.label);
                return;
            }
            IrValue value = ((IrNodes.Return) terminator).getValue();
            if (value.getType() == IrType.I64) {
                emitLongStore(longReturnRegister, value.getId());
                writer.u8(OP_LRETURN);
                writer.u16(longReturnRegister);
            } else {
                writer.u8(OP_RETURN_I32);
                writer.u16(value.getId());
            }
        }

        private void emitPhiCopies(IrBlock predecessor, IrBlock target) {
            for (int i = 0; i < target.getPhis().size(); i++) {
                IrPhi phi = target.getPhis().get(i);
                IrValue incoming = phi.getIncoming().get(predecessor);
                if (incoming == null) {
                    throw new UnsupportedIrConstructException(
                            "Missing phi input from " + predecessor.getName() + " to "
                                    + target.getName());
                }
                emitMove(baseRegisterCount + i, incoming.getId());
            }
            for (int i = 0; i < target.getPhis().size(); i++) {
                emitMove(target.getPhis().get(i).getResult().getId(),
                        baseRegisterCount + i);
            }
        }

        private void emitMove(int destination, int source) {
            writer.u8(OP_MOVE);
            writer.u16(destination);
            writer.u16(source);
        }

        private void emitLongStore(int destination, int source) {
            writer.u8(OP_LSTORE);
            writer.u16(destination);
            writer.u16(source);
        }

        private void emitJump(Label target) {
            writer.u8(OP_JUMP);
            emitTarget(target);
        }

        private void emitTarget(Label target) {
            int position = writer.reserveU32();
            patches.add(new Patch(position, target));
        }

        private Label requiredBlockLabel(IrBlock block) {
            Label label = blockLabels.get(block);
            if (label == null) {
                throw new UnsupportedIrConstructException(
                        "Evaluator CFG target is not part of the IR method");
            }
            return label;
        }

        private void mark(Label label) {
            if (label.position >= 0) {
                throw new IllegalStateException("Evaluator label emitted twice");
            }
            label.position = writer.size();
        }

        private void resolvePatches() {
            for (Patch patch : patches) {
                if (patch.target.position < 0) {
                    throw new UnsupportedIrConstructException(
                            "Evaluator CFG contains an unresolved target");
                }
                writer.patchU32(patch.position, patch.target.position);
            }
        }

        private int conditionCode(IrNodes.Branch.Condition condition) {
            switch (condition) {
                case EQ:
                    return 0;
                case NE:
                    return 1;
                case LT:
                    return 2;
                case GE:
                    return 3;
                case GT:
                    return 4;
                case LE:
                    return 5;
                default:
                    throw new IllegalStateException("Unknown branch condition " + condition);
            }
        }
    }

    private static final class Label {
        private int position = -1;
    }

    private static final class Patch {
        private final int position;
        private final Label target;

        private Patch(int position, Label target) {
            this.position = position;
            this.target = target;
        }
    }

    private static final class Edge {
        private final IrBlock predecessor;
        private final IrBlock target;
        private final Label label = new Label();

        private Edge(IrBlock predecessor, IrBlock target) {
            this.predecessor = predecessor;
            this.target = target;
        }
    }

    private static final class ByteWriter {
        private final List<Byte> bytes = new ArrayList<>();

        private int size() {
            return bytes.size();
        }

        private void u8(int value) {
            bytes.add((byte) (value & 0xff));
        }

        private void u16(int value) {
            u8(value);
            u8(value >>> 8);
        }

        private void i32(int value) {
            u32(value);
        }

        private void u32(int value) {
            u8(value);
            u8(value >>> 8);
            u8(value >>> 16);
            u8(value >>> 24);
        }

        private int reserveU32() {
            int position = size();
            u32(0);
            return position;
        }

        private void patchU32(int position, int value) {
            for (int i = 0; i < 4; i++) {
                bytes.set(position + i, (byte) ((value >>> (i * 8)) & 0xff));
            }
        }

        private byte[] toByteArray() {
            byte[] result = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                result[i] = bytes.get(i);
            }
            return result;
        }
    }
}
