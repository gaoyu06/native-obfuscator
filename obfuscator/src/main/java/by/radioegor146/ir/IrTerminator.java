package by.radioegor146.ir;

import java.util.List;

/**
 * The final control-flow operation in a basic block.
 */
public interface IrTerminator {
    List<IrBlock> getSuccessors();

    int getBytecodeOffset();
}
