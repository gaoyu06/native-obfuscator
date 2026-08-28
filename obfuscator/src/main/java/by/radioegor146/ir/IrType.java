package by.radioegor146.ir;

/**
 * Value types supported by the phase-one IR.
 */
public enum IrType {
    I32("i32", "jint", 1),
    I64("i64", "jlong", 2),
    REFERENCE("ref", "jobject", 1),
    VOID("void", "void", 0);

    private final String displayName;
    private final String cppType;
    private final int jvmSlots;

    IrType(String displayName, String cppType, int jvmSlots) {
        this.displayName = displayName;
        this.cppType = cppType;
        this.jvmSlots = jvmSlots;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCppType() {
        return cppType;
    }

    public int getJvmSlots() {
        return jvmSlots;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
