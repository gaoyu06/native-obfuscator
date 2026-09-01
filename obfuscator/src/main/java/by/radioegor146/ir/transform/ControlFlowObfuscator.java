package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrMethod;

import java.util.Objects;

/**
 * Basic IR control-flow obfuscation: phi lowering, fake branches, flattening,
 * then a deterministic non-entry block shuffle.
 */
public final class ControlFlowObfuscator {
    private ControlFlowObfuscator() {
    }

    public static void apply(IrMethod method) {
        Objects.requireNonNull(method, "method");
        IrPhiLowering.lower(method);
        FakeBranchInserter.insert(method);
        ControlFlowFlattener.flatten(method);
        BlockOrderShuffler.shuffle(method);
    }
}
