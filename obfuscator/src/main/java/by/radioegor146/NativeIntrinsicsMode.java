package by.radioegor146;

/**
 * Selects which Java library calls the IR path may replace with dedicated
 * native helpers instead of {@code Call*Method}.
 *
 * <p>{@link #SAFE} is the default. Arithmetic and control flow are always
 * emitted as C++ regardless of this setting. File and network I/O are never
 * rewritten.</p>
 */
public enum NativeIntrinsicsMode {
    OFF,
    SAFE,
    FAST;

    public boolean stringHelpers() {
        return this != OFF;
    }

    public boolean mathHelpers() {
        return this != OFF;
    }

    public boolean arraycopyHelper() {
        return this != OFF;
    }

    public boolean bitHelpers() {
        return this == FAST;
    }
}
