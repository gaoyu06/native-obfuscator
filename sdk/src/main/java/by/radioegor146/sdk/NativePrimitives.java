/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

import javax.crypto.AEADBadTagException;

/**
 * @deprecated Use {@link by.radioegor146.nativeobfuscator.NativePrimitives}.
 * This class remains in generated JARs so existing callers keep compiling.
 */
@Deprecated
public final class NativePrimitives {

    private NativePrimitives() {
    }

    public static int abiVersion() {
        return by.radioegor146.nativeobfuscator.NativePrimitives.abiVersion();
    }

    public static byte[] sha256(byte[] input) {
        return by.radioegor146.nativeobfuscator.NativePrimitives.sha256(input);
    }

    public static byte[] hmacSha256(byte[] key, byte[] message) {
        return by.radioegor146.nativeobfuscator.NativePrimitives.hmacSha256(key, message);
    }

    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext) {
        return by.radioegor146.nativeobfuscator.NativePrimitives
                .aes256GcmEncrypt(key, nonce, plaintext);
    }

    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) {
        return by.radioegor146.nativeobfuscator.NativePrimitives
                .aes256GcmEncrypt(key, nonce, plaintext, aad);
    }

    public static byte[] aes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag)
            throws AEADBadTagException {
        return by.radioegor146.nativeobfuscator.NativePrimitives
                .aes256GcmDecrypt(key, nonce, ciphertextAndTag);
    }

    public static byte[] aes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag, byte[] aad)
            throws AEADBadTagException {
        return by.radioegor146.nativeobfuscator.NativePrimitives
                .aes256GcmDecrypt(key, nonce, ciphertextAndTag, aad);
    }

    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        return by.radioegor146.nativeobfuscator.NativePrimitives
                .constantTimeEquals(left, right);
    }
}
