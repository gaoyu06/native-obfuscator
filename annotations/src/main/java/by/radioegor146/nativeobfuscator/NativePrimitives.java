package by.radioegor146.nativeobfuscator;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

/**
 * Byte primitives with a Java fallback. After native obfuscation, calls from
 * nativized methods are replaced with the C++ implementations packed into the
 * generated library; this class remains callable from plain Java as well.
 */
public final class NativePrimitives {

    private static final int AES_256_KEY_SIZE = 32;
    private static final int GCM_NONCE_SIZE = 12;
    private static final int GCM_TAG_SIZE = 16;
    private static final byte[] EMPTY_AAD = new byte[0];
    private static volatile boolean nativesReady;

    private NativePrimitives() {
    }

    private static boolean nativesReady() {
        if (nativesReady) {
            return true;
        }
        synchronized (NativePrimitives.class) {
            if (nativesReady) {
                return true;
            }
            try {
                nativeAbiVersion();
                nativesReady = true;
            } catch (Throwable ignored) {
            }
            return nativesReady;
        }
    }

    public static int abiVersion() {
        if (nativesReady()) {
            return nativeAbiVersion();
        }
        return 1;
    }

    public static byte[] sha256(byte[] input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (nativesReady()) {
            return nativeSha256(input);
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new InternalError("SHA-256", e);
        }
    }

    public static byte[] hmacSha256(byte[] key, byte[] message) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (message == null) {
            throw new NullPointerException("message");
        }
        if (nativesReady()) {
            return nativeHmacSha256(key, message);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new InternalError("HMAC-SHA-256", e);
        }
    }

    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext) {
        return aes256GcmEncrypt(key, nonce, plaintext, EMPTY_AAD);
    }

    public static byte[] aes256GcmEncrypt(
            byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) {
        validateAes256GcmInputs(key, nonce, plaintext, "plaintext");
        if (aad == null) {
            throw new NullPointerException("aad");
        }
        if (nativesReady()) {
            return nativeAes256GcmEncrypt(key, nonce, plaintext, aad);
        }
        return aes256Gcm(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad);
    }

    public static byte[] aes256GcmDecrypt(
            byte[] key, byte[] nonce, byte[] ciphertextAndTag)
            throws AEADBadTagException {
        return aes256GcmDecrypt(key, nonce, ciphertextAndTag, EMPTY_AAD);
    }

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
        if (nativesReady()) {
            return nativeAes256GcmDecrypt(key, nonce, ciphertextAndTag, aad);
        }
        try {
            return aes256Gcm(Cipher.DECRYPT_MODE, key, nonce, ciphertextAndTag, aad);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof AEADBadTagException) {
                throw (AEADBadTagException) e.getCause();
            }
            throw e;
        }
    }

    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null) {
            throw new NullPointerException("left");
        }
        if (right == null) {
            throw new NullPointerException("right");
        }
        if (nativesReady()) {
            return nativeConstantTimeEquals(left, right);
        }
        if (left.length != right.length) {
            return false;
        }
        int different = 0;
        for (int i = 0; i < left.length; i++) {
            different |= left[i] ^ right[i];
        }
        return different == 0;
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

    private static byte[] aes256Gcm(
            int mode, byte[] key, byte[] nonce, byte[] input, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_SIZE * 8, nonce));
            if (aad.length != 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(input);
        } catch (AEADBadTagException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new InternalError("AES-256-GCM", e);
        }
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
