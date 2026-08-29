package by.radioegor146.ir;

import java.util.Objects;

/**
 * A typed SSA value. Parameters carry their final C++ parameter name; all other
 * values are assigned a deterministic numeric name by {@link IrMethod}.
 */
public final class IrValue {
    public enum Kind {
        PARAMETER,
        INSTRUCTION,
        PHI
    }

    private final int id;
    private final IrType type;
    private final Kind kind;
    private final String debugName;
    private final String cppParameterName;
    private final String referenceDescriptor;

    IrValue(int id, IrType type, Kind kind, String debugName, String cppParameterName) {
        this(id, type, kind, debugName, cppParameterName, null);
    }

    IrValue(int id, IrType type, Kind kind, String debugName, String cppParameterName,
            String referenceDescriptor) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.debugName = Objects.requireNonNull(debugName, "debugName");
        this.cppParameterName = cppParameterName;
        if (referenceDescriptor != null && type != IrType.REFERENCE) {
            throw new IllegalArgumentException(
                    "Only reference values may carry a reference descriptor");
        }
        this.referenceDescriptor = referenceDescriptor;
    }

    public int getId() {
        return id;
    }

    public IrType getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDebugName() {
        return debugName;
    }

    public String getCppParameterName() {
        return cppParameterName;
    }

    /**
     * Returns the verifier-known JVM descriptor when this reference has one.
     * A null value means that the IR carrier is intentionally less precise.
     */
    public String getReferenceDescriptor() {
        return referenceDescriptor;
    }

    public String getIrName() {
        return "%" + debugName;
    }

    @Override
    public String toString() {
        return getIrName();
    }
}
