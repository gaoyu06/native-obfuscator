/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

/**
 * One-shot native byte primitives supplied by generated native libraries.
 *
 * <p>This class is added to generated JARs by native-obfuscator. Loading the
 * class initializes the generated library loader before a native entry point
 * is invoked.</p>
 */
public final class NativePrimitives {

    private NativePrimitives() {
    }

    /**
     * Returns the native SDK ABI major version.
     */
    public static int abiVersion() {
        return nativeAbiVersion();
    }

    /**
     * Computes the 32-byte SHA-256 digest of {@code input}.
     *
     * @throws NullPointerException if {@code input} is {@code null}
     */
    public static byte[] sha256(byte[] input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        return nativeSha256(input);
    }

    /**
     * Computes the 32-byte HMAC-SHA-256 tag of {@code message} using
     * {@code key}.
     *
     * @throws NullPointerException if either argument is {@code null}
     */
    public static byte[] hmacSha256(byte[] key, byte[] message) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (message == null) {
            throw new NullPointerException("message");
        }
        return nativeHmacSha256(key, message);
    }

    /**
     * Compares two arrays without data-dependent exits when their lengths are
     * equal. Length comparison is outside that scope, so unequal lengths
     * return {@code false} before comparing content.
     *
     * @throws NullPointerException if either argument is {@code null}
     */
    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null) {
            throw new NullPointerException("left");
        }
        if (right == null) {
            throw new NullPointerException("right");
        }
        return nativeConstantTimeEquals(left, right);
    }

    private static native int nativeAbiVersion();

    private static native byte[] nativeSha256(byte[] input);

    private static native byte[] nativeHmacSha256(byte[] key, byte[] message);

    private static native boolean nativeConstantTimeEquals(byte[] left, byte[] right);
}
