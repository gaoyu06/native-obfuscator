package by.radioegor146.nativeobfuscator;

/**
 * Per-class or per-method same-library native-to-native C++ calls.
 * {@link #INHERIT} uses the CLI {@code --ir-direct-native} value.
 */
public enum NativeDirectNative {
    INHERIT,
    OFF,
    ON
}
