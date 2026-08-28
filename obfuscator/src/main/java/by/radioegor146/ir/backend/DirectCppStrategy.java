package by.radioegor146.ir.backend;

import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.emit.IrCppEmitter;

import java.util.Objects;

/**
 * Existing straight-line structured C++ lowering.
 */
public final class DirectCppStrategy implements MethodLoweringStrategy {
    private final IrCppEmitter emitter;

    public DirectCppStrategy() {
        this(new IrCppEmitter());
    }

    public DirectCppStrategy(IrCppEmitter emitter) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
    }

    @Override
    public boolean supports(IrMethod method) {
        return method != null;
    }

    @Override
    public LoweredMethod lower(IrMethod method, LoweringContext context) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(context, "context");
        return LoweredMethod.direct(
                emitter.emitBody(method, context.getMethodContext()));
    }
}
