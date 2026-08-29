package by.radioegor146.ir;

/**
 * A non-terminating instruction that defines zero or one typed SSA values.
 */
public interface IrInstruction {
    /**
     * Returns null for side-effect-only instructions.
     */
    IrValue getResult();

    int getBytecodeOffset();
}
