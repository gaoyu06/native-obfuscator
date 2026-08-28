package by.radioegor146.ir;

/**
 * A non-terminating instruction that defines one typed SSA value.
 */
public interface IrInstruction {
    IrValue getResult();

    int getBytecodeOffset();
}
