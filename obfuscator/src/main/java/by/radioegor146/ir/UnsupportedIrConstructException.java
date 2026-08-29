package by.radioegor146.ir;

/**
 * Signals a capability miss that is safe to handle with per-method legacy
 * fallback. The frontend throws this before code generation mutates the method.
 */
public final class UnsupportedIrConstructException extends RuntimeException {
    private final int bytecodeOffset;
    private final int opcode;

    public UnsupportedIrConstructException(String message) {
        this(message, -1, -1);
    }

    public UnsupportedIrConstructException(String message, int bytecodeOffset, int opcode) {
        super(message + formatLocation(bytecodeOffset, opcode));
        this.bytecodeOffset = bytecodeOffset;
        this.opcode = opcode;
    }

    public int getBytecodeOffset() {
        return bytecodeOffset;
    }

    public int getOpcode() {
        return opcode;
    }

    private static String formatLocation(int bytecodeOffset, int opcode) {
        if (bytecodeOffset < 0) {
            return "";
        }
        return " at bytecode instruction " + bytecodeOffset
                + (opcode < 0 ? "" : " (opcode " + opcode + ")");
    }
}
