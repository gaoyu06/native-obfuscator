/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

/**
 * Native UTF-16 string operations supplied by generated native libraries.
 *
 * <p>The methods use the same UTF-16 code-unit length and hash algorithm as
 * {@link String}. This class is added to generated JARs and initialized with
 * the generated library loader.</p>
 */
public final class NativeStrings {

    private NativeStrings() {
    }

    /**
     * Returns the number of UTF-16 code units in {@code value}.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static int length(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return nativeLength(value);
    }

    /**
     * Returns the same polynomial hash as {@link String#hashCode()}.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static int hashCode(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return nativeHashCode(value);
    }

    /**
     * Returns the UTF-16 code units of {@code left} followed by {@code right}.
     *
     * @throws NullPointerException if either argument is {@code null}
     */
    public static String concat(String left, String right) {
        if (left == null) {
            throw new NullPointerException("left");
        }
        if (right == null) {
            throw new NullPointerException("right");
        }
        return nativeConcat(left, right);
    }

    private static native int nativeLength(String value);

    private static native int nativeHashCode(String value);

    private static native String nativeConcat(String left, String right);
}
