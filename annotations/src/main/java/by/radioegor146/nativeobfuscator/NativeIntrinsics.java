package by.radioegor146.nativeobfuscator;

/**
 * Per-class or per-method JDK intrinsic set. {@link #INHERIT} uses the CLI
 * {@code --native-intrinsics} value.
 */
public enum NativeIntrinsics {
    INHERIT,
    OFF,
    SAFE,
    FAST
}
