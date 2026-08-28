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
            out.append(":\n");
            for (IrInstruction instruction : block.getInstructions()) {
                out.append("  ").append(formatInstruction(instruction)).append("\n");
            }
            out.append("  ").append(formatTerminator(block.getTerminator())).append("\n");
        }
        return out.toString();
    }

    private String formatInstruction(IrInstruction instruction) {
        if (instruction instanceof IrNodes.Const) {
            IrNodes.Const constant = (IrNodes.Const) instruction;
            return constant.getResult() + ":" + constant.getResult().getType()
                    + " = iconst " + constant.getValue();
        }
        if (instruction instanceof IrNodes.Binary) {
            IrNodes.Binary binary = (IrNodes.Binary) instruction;
            return binary.getResult() + ":" + binary.getResult().getType() + " = "
                    + binary.getOperation().getMnemonic() + " " + binary.getLeft()
                    + ", " + binary.getRight();
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
        if (terminator instanceof IrNodes.Return) {
            IrValue value = ((IrNodes.Return) terminator).getValue();
            return value == null ? "return" : "return " + value;
        }
        throw new IllegalStateException("Unknown IR terminator " + terminator.getClass());
    }
}
