package by.radioegor146.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A basic block with explicit phi merges and exactly one terminator.
 */
public final class IrBlock {
    private final int id;
    private final List<IrPhi> phis = new ArrayList<>();
    private final List<IrInstruction> instructions = new ArrayList<>();
    private final List<IrExceptionEdge> exceptionEdges = new ArrayList<>();
    private IrTerminator terminator;

    IrBlock(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return "b" + id;
    }

    public List<IrPhi> getPhis() {
        return Collections.unmodifiableList(phis);
    }

    public List<IrInstruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public IrTerminator getTerminator() {
        return terminator;
    }

    public List<IrExceptionEdge> getExceptionEdges() {
        return Collections.unmodifiableList(exceptionEdges);
    }

    public void addPhi(IrPhi phi) {
        phis.add(Objects.requireNonNull(phi, "phi"));
    }

    public void addInstruction(IrInstruction instruction) {
        instructions.add(Objects.requireNonNull(instruction, "instruction"));
    }

    public void addExceptionEdge(IrExceptionEdge edge) {
        exceptionEdges.add(Objects.requireNonNull(edge, "edge"));
    }

    public void clearExceptionEdges() {
        exceptionEdges.clear();
    }

    public void clearPhis() {
        phis.clear();
    }

    public void setTerminator(IrTerminator terminator) {
        if (this.terminator != null) {
            throw new IllegalStateException("Terminator already set for " + getName());
        }
        this.terminator = Objects.requireNonNull(terminator, "terminator");
    }

    public void replaceTerminator(IrTerminator terminator) {
        this.terminator = Objects.requireNonNull(terminator, "terminator");
    }

    @Override
    public String toString() {
        return getName();
    }
}
