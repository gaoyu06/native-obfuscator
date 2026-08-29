/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

import javax.crypto.AEADBadTagException;

/**
 * One-shot native byte primitives supplied by generated native libraries.
 *
 * <p>This class is added to generated JARs by native-obfuscator. Loading the
 * class initializes the generated library loader before a native entry point
 * is invoked.</p>
 */
public final class NativePrimitives {

    private static final int AES_256_KEY_SIZE = 32;
    private static final int GCM_NONCE_SIZE = 12;
    private static final int GCM_TAG_SIZE = 16;
    private static final byte[] EMPTY_AAD = new byte[0];

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
     * Encrypts {@code plaintext} using AES-256-GCM with no additional
     * authenticated data.
     *
     * <p>The returned array is the ciphertext followed by the 16-byte
     * authentication tag. A nonce must never be reused with the same key.</p>
     *
     * @param key 32-byte AES-256 key
     * @param nonce 12-byte (96-bit) GCM nonce
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the key or nonce length is invalid
     */
    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext) {
        return aes256GcmEncrypt(key, nonce, plaintext, EMPTY_AAD);
    }

    /**
     * Encrypts {@code plaintext} and authenticates {@code aad} using
     * AES-256-GCM.
     *
     * <p>The returned array is the ciphertext followed by the 16-byte
     * authentication tag. A nonce must never be reused with the same key.</p>
     *
     * @param key 32-byte AES-256 key
     * @param nonce 12-byte (96-bit) GCM nonce
     * @param aad additional authenticated data; an empty array means no AAD
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the key or nonce length is invalid
     */
    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) {
        validateAes256GcmInputs(key, nonce, plaintext, "plaintext");
        if (aad == null) {
            throw new NullPointerException("aad");
        }
        return nativeAes256GcmEncrypt(key, nonce, plaintext, aad);
    }

    /**
     * Authenticates and decrypts AES-256-GCM ciphertext with no additional
     * authenticated data.
     *
     * @param ciphertextAndTag ciphertext followed by its 16-byte tag
     * @throws AEADBadTagException if authentication fails; no plaintext is
     * returned in this case
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a length is invalid
     */
    public static byte[] aes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag)
            throws AEADBadTagException {
        return aes256GcmDecrypt(key, nonce, ciphertextAndTag, EMPTY_AAD);
    }

    /**
     * Authenticates {@code aad} and decrypts AES-256-GCM ciphertext.
     *
     * <p>Authentication is completed before plaintext is returned.</p>
     *
     * @param key 32-byte AES-256 key
     * @param nonce 12-byte (96-bit) GCM nonce
     * @param ciphertextAndTag ciphertext followed by its 16-byte tag
     * @param aad additional authenticated data; an empty array means no AAD
     * @throws AEADBadTagException if authentication fails; no plaintext is
     * returned in this case
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a length is invalid
     */
    public static byte[] aes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag, byte[] aad)
            throws AEADBadTagException {
        validateAes256GcmInputs(key, nonce, ciphertextAndTag, "ciphertextAndTag");
        if (aad == null) {
            throw new NullPointerException("aad");
        }
        if (ciphertextAndTag.length < GCM_TAG_SIZE) {
            throw new IllegalArgumentException(
                    "ciphertextAndTag must include a 16-byte authentication tag");
        }
        return nativeAes256GcmDecrypt(key, nonce, ciphertextAndTag, aad);
    }

    private static void validateAes256GcmInputs(
            byte[] key, byte[] nonce, byte[] input, String inputName) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (nonce == null) {
            throw new NullPointerException("nonce");
        }
        if (input == null) {
            throw new NullPointerException(inputName);
        }
        if (key.length != AES_256_KEY_SIZE) {
            throw new IllegalArgumentException("key must contain exactly 32 bytes");
        }
        if (nonce.length != GCM_NONCE_SIZE) {
            throw new IllegalArgumentException("nonce must contain exactly 12 bytes");
        }
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

    private static native byte[] nativeAes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext, byte[] aad);

    private static native byte[] nativeAes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag, byte[] aad)
            throws AEADBadTagException;

    private static native boolean nativeConstantTimeEquals(byte[] left, byte[] right);
}
