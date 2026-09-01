package by.radioegor146.nativeobfuscator;

/**
 * Per-class or per-method control-flow obfuscation. {@link #INHERIT} uses the
 * CLI {@code --ir-cf-obf} value.
 */
public enum NativeCfObfuscation {
    INHERIT,
    OFF,
    BASIC
}
