package by.radioegor146.nativeobfuscator;

/**
 * Per-class or per-method IR lowering. {@link #INHERIT} uses the CLI
 * {@code --ir-lower} value.
 */
public enum NativeLowering {
    INHERIT,
    DIRECT,
    EVAL
}
