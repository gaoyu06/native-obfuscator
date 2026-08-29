/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

public final class NativePrimitivesVerifier {

    private NativePrimitivesVerifier() {
    }

    public static void main(String[] args) throws Exception {
        require(NativePrimitives.abiVersion() == 1, "ABI version");

        verifyVector(
                new byte[0],
                "e3b0c44298fc1c149afbf4c8996fb924"
                        + "27ae41e4649b934ca495991b7852b855");
        verifyVector(
                "abc".getBytes(StandardCharsets.US_ASCII),
                "ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad");

        verifyHmacVector(
                new byte[0],
                "My test data".getBytes(StandardCharsets.US_ASCII),
                "2274b195d90ce8e03406f4b526a47e07"
                        + "87a88a65479938f1a5baa3ce0f079776",
                "BoringSSL empty-key vector");
        verifyHmacVector(
                "Jefe".getBytes(StandardCharsets.US_ASCII),
                "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII),
                "5bdcc146bf60754e6a042426089575c7"
                        + "5a003f089d2739839dec58b964ec3843",
                "RFC 4231 test case 2");
        byte[] longKey = new byte[131];
        Arrays.fill(longKey, (byte) 0xaa);
        verifyHmacVector(
                longKey,
                "Test Using Larger Than Block-Size Key - Hash Key First"
                        .getBytes(StandardCharsets.US_ASCII),
                "60e431591ee0b67f0d8a26aacbf5b77f"
                        + "8e0bc6213728c5140546040f0ee37f54",
                "RFC 4231 test case 6");
        verifyHmacVector(
                fromHex("1e225cafb90339bba1b24076d4206c3e"
                        + "79c355805d851682bc818baa4f5a7779"),
                new byte[0],
                "b175b57d89ea6cb606fb3363f2538abd"
                        + "73a4c00b4a1386905bac809004cf1933",
                "Wycheproof tcId 1");

        verifyAesGcmVector(
                "b52c505a37d78eda5dd34f20c22540ea"
                        + "1b58963cf8e5bf8ffa85f9f2492505b4",
                "516c33929df5a3284ff463d7",
                "",
                "",
                "",
                "bdc1ac884d332457a1d2664f168c76f0",
                "NIST GCMVS AES-256 PTlen=0 AADlen=0 Taglen=128 Count=0");
        verifyAesGcmVector(
                "31bdadd96698c204aa9ce1448ea94ae1"
                        + "fb4a9a0b3c9d773b51bb1822666b8f22",
                "0d18e06c7c725ac9e362e1ce",
                "2db5168e932556f8089a0622981d017d",
                "",
                "fa4362189661d163fcd6a56d8bf0405a",
                "d636ac1bbedd5cc3ee727dc2ab4a9489",
                "NIST GCMVS AES-256 PTlen=128 AADlen=0 Taglen=128 Count=0");
        verifyAesGcmVector(
                "24501ad384e473963d476edcfe082052"
                        + "37acfd49b5b8f33857f8114e863fec7f",
                "9ff18563b978ec281b3f2794",
                "27f348f9cdc0c5bd5e66b1ccb63ad920"
                        + "ff2219d14e8d631b3872265cf117ee867"
                        + "57accb158bd9abb3868fdc0d0b074b5f01b2c",
                "adb5ec720ccf9898500028bf34afccbcaca126ef",
                "eb7cb754c824e8d96f7c6d9b76c7d26"
                        + "fb874ffbf1d65c6f64a698d839b0b061"
                        + "45dae82057ad55994cf59ad7f67c0fa5e85fab8",
                "bc95c532fecc594c36d1550286a7a3f0",
                "NIST GCMVS AES-256 PTlen=408 AADlen=160 Taglen=128 Count=0");
        verifyAesGcmAuthenticationFailure();
        verifyAesGcmContracts();

        MessageDigest reference = MessageDigest.getInstance("SHA-256");
        Random random = new Random(4230L);
        int[] lengths = {0, 1, 55, 56, 63, 64, 65, 1024, 65536};
        for (int length : lengths) {
            byte[] input = new byte[length];
            random.nextBytes(input);
            require(
                    Arrays.equals(reference.digest(input), NativePrimitives.sha256(input)),
                    "MessageDigest mismatch at length " + length);
        }

        require(
                NativePrimitives.constantTimeEquals(new byte[0], new byte[0]),
                "empty equality");
        byte[] baseline = new byte[257];
        random.nextBytes(baseline);
        require(
                NativePrimitives.constantTimeEquals(
                        baseline, Arrays.copyOf(baseline, baseline.length)),
                "equal content");

        byte[] firstMismatch = Arrays.copyOf(baseline, baseline.length);
        firstMismatch[0] ^= 1;
        require(
                !NativePrimitives.constantTimeEquals(baseline, firstMismatch),
                "first-byte mismatch");

        byte[] lastMismatch = Arrays.copyOf(baseline, baseline.length);
        lastMismatch[lastMismatch.length - 1] ^= 1;
        require(
                !NativePrimitives.constantTimeEquals(baseline, lastMismatch),
                "last-byte mismatch");
        require(
                !NativePrimitives.constantTimeEquals(baseline, new byte[1]),
                "length mismatch");

        expectNullPointer(() -> NativePrimitives.sha256(null));
        expectNullPointer(() -> NativePrimitives.hmacSha256(null, new byte[0]));
        expectNullPointer(() -> NativePrimitives.hmacSha256(new byte[0], null));
        expectNullPointer(() -> NativePrimitives.constantTimeEquals(null, new byte[0]));
        expectNullPointer(() -> NativePrimitives.constantTimeEquals(new byte[0], null));

        verifyStrings();

        System.out.println(
                "NativePrimitivesVerifier: PASS "
                        + "(2 FIPS vectors, 4 HMAC-SHA-256 vectors, "
                        + "3 NIST AES-256-GCM vectors, "
                        + "4 AES-GCM authentication failures, "
                        + "5 AES-GCM length checks, 8 AES-GCM null checks, "
                        + "9 MessageDigest cases, 5 equality cases, 5 string vectors, "
                        + "4 concatenations)");
    }

    private static void verifyStrings() throws Exception {
        String[] inputs = {
                "",
                "plain ASCII",
                "你好，世界",
                "A\uD83D\uDE00Z",
                "\u0000embedded\u0000nulls"
        };
        for (String input : inputs) {
            require(
                    NativeStrings.length(input) == input.length(),
                    "String.length mismatch for " + printable(input));
            require(
                    NativeStrings.hashCode(input) == input.hashCode(),
                    "String.hashCode mismatch for " + printable(input));
        }

        verifyConcat("", "");
        verifyConcat("", "suffix");
        verifyConcat("prefix", "");
        verifyConcat("你好", "\uD83D\uDE00world");

        expectNullPointer(() -> NativeStrings.length(null));
        expectNullPointer(() -> NativeStrings.hashCode(null));
        expectNullPointer(() -> NativeStrings.concat(null, ""));
        expectNullPointer(() -> NativeStrings.concat("", null));
    }

    private static void verifyConcat(String left, String right) {
        require(
                NativeStrings.concat(left, right).equals(left.concat(right)),
                "String.concat mismatch for " + printable(left) + " + " + printable(right));
    }

    private static String printable(String value) {
        return value.replace("\u0000", "\\0");
    }

    private static void verifyVector(byte[] input, String expectedHex) {
        require(
                expectedHex.equals(toHex(NativePrimitives.sha256(input))),
                "known SHA-256 vector");
    }

    private static void verifyHmacVector(
            byte[] key, byte[] message, String expectedHex, String label) {
        require(
                expectedHex.equals(toHex(NativePrimitives.hmacSha256(key, message))),
                label);
    }

    private static void verifyAesGcmVector(
            String keyHex,
            String nonceHex,
            String plaintextHex,
            String aadHex,
            String ciphertextHex,
            String tagHex,
            String label) throws Exception {
        byte[] key = fromHex(keyHex);
        byte[] nonce = fromHex(nonceHex);
        byte[] plaintext = fromHex(plaintextHex);
        byte[] aad = fromHex(aadHex);
        byte[] expected = concatenate(fromHex(ciphertextHex), fromHex(tagHex));

        byte[] encrypted = aad.length == 0
                ? NativePrimitives.aes256GcmEncrypt(key, nonce, plaintext)
                : NativePrimitives.aes256GcmEncrypt(key, nonce, plaintext, aad);
        require(Arrays.equals(expected, encrypted), label + " encryption");

        byte[] decrypted = aad.length == 0
                ? NativePrimitives.aes256GcmDecrypt(key, nonce, encrypted)
                : NativePrimitives.aes256GcmDecrypt(key, nonce, encrypted, aad);
        require(Arrays.equals(plaintext, decrypted), label + " decryption");
    }

    private static void verifyAesGcmAuthenticationFailure() throws Exception {
        byte[] key = fromHex(
                "24501ad384e473963d476edcfe082052"
                        + "37acfd49b5b8f33857f8114e863fec7f");
        byte[] nonce = fromHex("9ff18563b978ec281b3f2794");
        byte[] plaintext = fromHex(
                "27f348f9cdc0c5bd5e66b1ccb63ad920"
                        + "ff2219d14e8d631b3872265cf117ee867"
                        + "57accb158bd9abb3868fdc0d0b074b5f01b2c");
        byte[] aad = fromHex("adb5ec720ccf9898500028bf34afccbcaca126ef");
        byte[] encrypted =
                NativePrimitives.aes256GcmEncrypt(key, nonce, plaintext, aad);

        byte[] changedTag = Arrays.copyOf(encrypted, encrypted.length);
        changedTag[changedTag.length - 1] ^= 1;
        expectAuthenticationFailure(
                () -> NativePrimitives.aes256GcmDecrypt(key, nonce, changedTag, aad));

        byte[] changedCiphertext = Arrays.copyOf(encrypted, encrypted.length);
        changedCiphertext[0] ^= 1;
        expectAuthenticationFailure(
                () -> NativePrimitives.aes256GcmDecrypt(
                        key, nonce, changedCiphertext, aad));

        byte[] changedAad = Arrays.copyOf(aad, aad.length);
        changedAad[0] ^= 1;
        expectAuthenticationFailure(
                () -> NativePrimitives.aes256GcmDecrypt(
                        key, nonce, encrypted, changedAad));

        byte[] changedKey = Arrays.copyOf(key, key.length);
        changedKey[0] ^= 1;
        expectAuthenticationFailure(
                () -> NativePrimitives.aes256GcmDecrypt(
                        changedKey, nonce, encrypted, aad));
    }

    private static void verifyAesGcmContracts() throws Exception {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] empty = new byte[0];
        byte[] tagOnly = NativePrimitives.aes256GcmEncrypt(key, nonce, empty);

        expectNullPointer(
                () -> NativePrimitives.aes256GcmEncrypt(null, nonce, empty, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmEncrypt(key, null, empty, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmEncrypt(key, nonce, null, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmEncrypt(key, nonce, empty, null));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmDecrypt(null, nonce, tagOnly, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmDecrypt(key, null, tagOnly, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmDecrypt(key, nonce, null, empty));
        expectNullPointer(
                () -> NativePrimitives.aes256GcmDecrypt(key, nonce, tagOnly, null));

        expectIllegalArgument(
                () -> NativePrimitives.aes256GcmEncrypt(
                        new byte[31], nonce, empty, empty));
        expectIllegalArgument(
                () -> NativePrimitives.aes256GcmEncrypt(
                        new byte[33], nonce, empty, empty));
        expectIllegalArgument(
                () -> NativePrimitives.aes256GcmEncrypt(
                        key, new byte[11], empty, empty));
        expectIllegalArgument(
                () -> NativePrimitives.aes256GcmEncrypt(
                        key, new byte[13], empty, empty));
        expectIllegalArgument(
                () -> NativePrimitives.aes256GcmDecrypt(
                        key, nonce, new byte[15], empty));
    }

    private static byte[] concatenate(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static byte[] fromHex(String hex) {
        byte[] output = new byte[hex.length() / 2];
        for (int i = 0; i < output.length; ++i) {
            output[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return output;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(String.format("%02x", value & 0xff));
        }
        return output.toString();
    }

    private static void expectNullPointer(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected API contract.
        }
    }

    private static void expectIllegalArgument(ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected API contract.
        }
    }

    private static void expectAuthenticationFailure(ThrowingRunnable action)
            throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected AEADBadTagException");
        } catch (AEADBadTagException expected) {
            // Expected API contract.
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
