package by.radioegor146.nativeobfuscator;

/**
 * Per-class or per-method compiler backend. {@link #INHERIT} uses the CLI
 * {@code --backend} value.
 */
public enum NativeBackend {
    INHERIT,
    CPP,
    INTERPRETER
}
