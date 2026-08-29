package by.radioegor146.ir;

import java.util.Objects;

/**
 * One ordered JVM exception-table entry covering an IR basic block.
 */
public final class IrExceptionEdge {
    private final String catchType;
    private final IrBlock handler;

    public IrExceptionEdge(String catchType, IrBlock handler) {
        this.catchType = catchType;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Null denotes a catch-all exception-table entry.
     */
    public String getCatchType() {
        return catchType;
    }

    public IrBlock getHandler() {
        return handler;
    }
}
