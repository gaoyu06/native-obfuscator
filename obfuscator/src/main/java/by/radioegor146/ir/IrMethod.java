package by.radioegor146.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A complete typed control-flow graph for one JVM method.
 */
public final class IrMethod {
    private final String owner;
    private final String name;
    private final String descriptor;
    private final boolean staticMethod;
    private final IrType returnType;
    private final List<IrValue> parameters = new ArrayList<>();
    private final List<IrBlock> blocks = new ArrayList<>();
    private int nextValueId;

    public IrMethod(String owner, String name, String descriptor, boolean staticMethod,
                    IrType returnType) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.staticMethod = staticMethod;
        this.returnType = Objects.requireNonNull(returnType, "returnType");
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isStaticMethod() {
        return staticMethod;
    }

    public IrType getReturnType() {
        return returnType;
    }

    public List<IrValue> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public List<IrBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public IrValue addParameter(IrType type, String debugName, String cppParameterName) {
        IrValue value = new IrValue(nextValueId++, type, IrValue.Kind.PARAMETER,
                debugName, cppParameterName);
        parameters.add(value);
        return value;
    }

    public IrValue newInstructionValue(IrType type) {
        int id = nextValueId++;
        return new IrValue(id, type, IrValue.Kind.INSTRUCTION, "v" + id, null);
    }

    public IrBlock addBlock() {
        IrBlock block = new IrBlock(blocks.size());
        blocks.add(block);
        return block;
    }

    public IrPhi addPhi(IrBlock block, IrType type, IrPhi.SlotKind slotKind, int slotIndex) {
        int id = nextValueId++;
        IrValue value = new IrValue(id, type, IrValue.Kind.PHI, "v" + id, null);
        IrPhi phi = new IrPhi(value, slotKind, slotIndex);
        block.addPhi(phi);
        return phi;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("method ").append(owner).append('.').append(name).append(descriptor)
                .append(" -> ").append(returnType)
                .append(" [").append(staticMethod ? "static" : "instance").append("]\n");
        out.append("  params: ");
        out.append(parameters.stream()
                .map(value -> value + ":" + value.getType())
                .collect(Collectors.joining(", ")));
        out.append("\n");

        for (IrBlock block : blocks) {
            out.append("block ").append(block.getName());
            if (!block.getPhis().isEmpty()) {
                out.append('(');
                out.append(block.getPhis().stream()
                        .map(phi -> phi.getResult() + ":" + phi.getResult().getType())
                        .collect(Collectors.joining(", ")));
                out.append(')');
            }
            if (!block.getExceptionEdges().isEmpty()) {
                out.append(" catches [");
                out.append(block.getExceptionEdges().stream()
                        .map(edge -> (edge.getCatchType() == null
                                ? "any" : edge.getCatchType())
                                + " -> " + edge.getHandler().getName())
                        .collect(Collectors.joining(", ")));
                out.append(']');
            }
            out.append(":\n");
            for (IrInstruction instruction : block.getInstructions()) {
                out.append("  ").append(formatInstruction(instruction)).append("\n");
            }
            out.append("  ").append(formatTerminator(block.getTerminator())).append("\n");
        }
        return out.toString();
    }

    private String formatInstruction(IrInstruction instruction) {
        if (instruction instanceof IrNodes.CaughtException) {
            IrNodes.CaughtException caught = (IrNodes.CaughtException) instruction;
            return caught.getResult() + ":" + caught.getResult().getType()
                    + " = caught_exception";
        }
        if (instruction instanceof IrNodes.Const) {
            IrNodes.Const constant = (IrNodes.Const) instruction;
            return constant.getResult() + ":" + constant.getResult().getType()
                    + " = iconst " + constant.getValue();
        }
        if (instruction instanceof IrNodes.LongConst) {
            IrNodes.LongConst constant = (IrNodes.LongConst) instruction;
            return constant.getResult() + ":" + constant.getResult().getType()
                    + " = lconst " + constant.getValue();
        }
        if (instruction instanceof IrNodes.Binary) {
            IrNodes.Binary binary = (IrNodes.Binary) instruction;
            return binary.getResult() + ":" + binary.getResult().getType() + " = "
                    + binary.getOperation().getMnemonic() + " " + binary.getLeft()
                    + ", " + binary.getRight();
        }
        if (instruction instanceof IrNodes.LongBinary) {
            IrNodes.LongBinary binary = (IrNodes.LongBinary) instruction;
            return binary.getResult() + ":" + binary.getResult().getType() + " = "
                    + binary.getOperation().getMnemonic() + " " + binary.getLeft()
                    + ", " + binary.getRight();
        }
        if (instruction instanceof IrNodes.IntDivRem) {
            IrNodes.IntDivRem binary = (IrNodes.IntDivRem) instruction;
            return binary.getResult() + ":" + binary.getResult().getType() + " = "
                    + binary.getOperation().getMnemonic() + " " + binary.getLeft()
                    + ", " + binary.getRight();
        }
        if (instruction instanceof IrNodes.Unary) {
            IrNodes.Unary unary = (IrNodes.Unary) instruction;
            return unary.getResult() + ":" + unary.getResult().getType() + " = "
                    + unary.getOperation().getMnemonic() + " " + unary.getOperand();
        }
        if (instruction instanceof IrNodes.Conversion) {
            IrNodes.Conversion conversion = (IrNodes.Conversion) instruction;
            return conversion.getResult() + ":" + conversion.getResult().getType() + " = "
                    + conversion.getOperation().getMnemonic() + " "
                    + conversion.getOperand();
        }
        if (instruction instanceof IrNodes.NewArray) {
            IrNodes.NewArray array = (IrNodes.NewArray) instruction;
            return array.getResult() + ":" + array.getResult().getType()
                    + " = newarray int " + array.getLength();
        }
        if (instruction instanceof IrNodes.NewObjectArray) {
            IrNodes.NewObjectArray array = (IrNodes.NewObjectArray) instruction;
            return array.getResult() + ":" + array.getResult().getType()
                    + " = anewarray " + array.getComponentType() + " " + array.getLength();
        }
        if (instruction instanceof IrNodes.ArrayLength) {
            IrNodes.ArrayLength length = (IrNodes.ArrayLength) instruction;
            return length.getResult() + ":" + length.getResult().getType()
                    + " = arraylength " + length.getArray();
        }
        if (instruction instanceof IrNodes.ArrayLoad) {
            IrNodes.ArrayLoad load = (IrNodes.ArrayLoad) instruction;
            return load.getResult() + ":" + load.getResult().getType() + " = iaload "
                    + load.getArray() + ", " + load.getIndex();
        }
        if (instruction instanceof IrNodes.ArrayStore) {
            IrNodes.ArrayStore store = (IrNodes.ArrayStore) instruction;
            return "iastore " + store.getArray() + ", " + store.getIndex() + ", "
                    + store.getValue();
        }
        if (instruction instanceof IrNodes.StringLength) {
            IrNodes.StringLength length = (IrNodes.StringLength) instruction;
            return length.getResult() + ":" + length.getResult().getType()
                    + " = stringlength " + length.getReceiver();
        }
        if (instruction instanceof IrNodes.CheckCast) {
            IrNodes.CheckCast checkCast = (IrNodes.CheckCast) instruction;
            return checkCast.getResult() + ":" + checkCast.getResult().getType()
                    + " = checkcast " + checkCast.getTargetType() + " "
                    + checkCast.getOperand();
        }
        if (instruction instanceof IrNodes.InstanceOf) {
            IrNodes.InstanceOf instanceOf = (IrNodes.InstanceOf) instruction;
            return instanceOf.getResult() + ":" + instanceOf.getResult().getType()
                    + " = instanceof " + instanceOf.getTargetType() + " "
                    + instanceOf.getOperand();
        }
        if (instruction instanceof IrNodes.GetField) {
            IrNodes.GetField field = (IrNodes.GetField) instruction;
            return field.getResult() + ":" + field.getResult().getType() + " = getfield "
                    + field.getOwner() + "." + field.getName() + ":" + field.getDescriptor()
                    + " " + field.getReceiver();
        }
        if (instruction instanceof IrNodes.PutField) {
            IrNodes.PutField field = (IrNodes.PutField) instruction;
            return "putfield " + field.getOwner() + "." + field.getName() + ":"
                    + field.getDescriptor() + " " + field.getReceiver() + ", "
                    + field.getValue();
        }
        if (instruction instanceof IrNodes.GetStaticField) {
            IrNodes.GetStaticField field = (IrNodes.GetStaticField) instruction;
            return field.getResult() + ":" + field.getResult().getType() + " = getstatic "
                    + field.getOwner() + "." + field.getName() + ":"
                    + field.getDescriptor();
        }
        if (instruction instanceof IrNodes.PutStaticField) {
            IrNodes.PutStaticField field = (IrNodes.PutStaticField) instruction;
            return "putstatic " + field.getOwner() + "." + field.getName() + ":"
                    + field.getDescriptor() + " " + field.getValue();
        }
        if (instruction instanceof IrNodes.Invoke) {
            IrNodes.Invoke invoke = (IrNodes.Invoke) instruction;
            String operands = invoke.getArguments().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            if (invoke.getReceiver() != null) {
                operands = invoke.getReceiver()
                        + (operands.isEmpty() ? "" : ", " + operands);
            }
            return invoke.getResult() + ":" + invoke.getResult().getType() + " = "
                    + invoke.getKind().getMnemonic() + " " + invoke.getOwner() + "."
                    + invoke.getName() + invoke.getDescriptor()
                    + (operands.isEmpty() ? "" : " " + operands);
        }
        throw new IllegalStateException("Unknown IR instruction " + instruction.getClass());
    }

    private String formatTerminator(IrTerminator terminator) {
        if (terminator instanceof IrNodes.Goto) {
            return "goto " + ((IrNodes.Goto) terminator).getTarget().getName();
        }
        if (terminator instanceof IrNodes.Branch) {
            IrNodes.Branch branch = (IrNodes.Branch) terminator;
            return "branch i" + branch.getCondition().getMnemonic() + " " + branch.getLeft()
                    + ", " + (branch.getRight() == null ? "0" : branch.getRight())
                    + " -> " + branch.getTrueTarget().getName() + ", "
                    + branch.getFalseTarget().getName();
        }
        if (terminator instanceof IrNodes.Switch) {
            IrNodes.Switch switchTerminator = (IrNodes.Switch) terminator;
            String cases = "";
            for (int i = 0; i < switchTerminator.getKeys().size(); i++) {
                if (i > 0) {
                    cases += ", ";
                }
                cases += switchTerminator.getKeys().get(i) + " -> "
                        + switchTerminator.getTargets().get(i).getName();
            }
            return "switch " + switchTerminator.getSelector() + " ["
                    + cases + (cases.isEmpty() ? "" : ", ")
                    + "default -> " + switchTerminator.getDefaultTarget().getName() + "]";
        }
        if (terminator instanceof IrNodes.Return) {
            IrValue value = ((IrNodes.Return) terminator).getValue();
            return value == null ? "return" : "return " + value;
        }
        if (terminator instanceof IrNodes.Throw) {
            return "throw " + ((IrNodes.Throw) terminator).getException();
        }
        throw new IllegalStateException("Unknown IR terminator " + terminator.getClass());
    }
}
