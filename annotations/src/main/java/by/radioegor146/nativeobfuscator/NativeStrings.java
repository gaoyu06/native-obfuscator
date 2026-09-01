package by.radioegor146.nativeobfuscator;

/**
 * UTF-16 string operations with a Java fallback. After native obfuscation,
 * calls from nativized methods are replaced with the C++ implementations.
 */
public final class NativeStrings {

    private static volatile boolean nativesReady;

    private NativeStrings() {
    }

    private static boolean nativesReady() {
        if (nativesReady) {
            return true;
        }
        synchronized (NativeStrings.class) {
            if (nativesReady) {
                return true;
            }
            try {
                nativeLength("");
                nativesReady = true;
            } catch (Throwable ignored) {
            }
            return nativesReady;
        }
    }

    public static int length(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (nativesReady()) {
            return nativeLength(value);
        }
        return value.length();
    }

    public static int hashCode(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (nativesReady()) {
            return nativeHashCode(value);
        }
        return value.hashCode();
    }

    public static String concat(String left, String right) {
        if (left == null) {
            throw new NullPointerException("left");
        }
        if (right == null) {
            throw new NullPointerException("right");
        }
        if (nativesReady()) {
            return nativeConcat(left, right);
        }
        return left.concat(right);
    }

    private static native int nativeLength(String value);

    private static native int nativeHashCode(String value);

    private static native String nativeConcat(String left, String right);
}
