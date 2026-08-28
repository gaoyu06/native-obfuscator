package by.radioegor146.ir.backend;

import by.radioegor146.MethodContext;

import java.util.Objects;

/**
 * Services available to an IR lowering strategy.
 */
public final class LoweringContext {
    private final MethodContext methodContext;

    public LoweringContext(MethodContext methodContext) {
        this.methodContext = Objects.requireNonNull(methodContext, "methodContext");
    }

    public MethodContext getMethodContext() {
        return methodContext;
    }
}
