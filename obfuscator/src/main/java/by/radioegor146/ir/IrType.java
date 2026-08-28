package by.radioegor146.ir;

/**
 * Value types supported by the phase-one IR.
 */
public enum IrType {
    I32("i32", "jint"),
    REFERENCE("ref", "jobject"),
    VOID("void", "void");

    private final String displayName;
    private final String cppType;

    IrType(String displayName, String cppType) {
        this.displayName = displayName;
        this.cppType = cppType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCppType() {
        return cppType;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
