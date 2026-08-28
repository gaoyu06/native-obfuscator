/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

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
        expectNullPointer(() -> NativePrimitives.constantTimeEquals(null, new byte[0]));
        expectNullPointer(() -> NativePrimitives.constantTimeEquals(new byte[0], null));

        verifyStrings();

        System.out.println(
                "NativePrimitivesVerifier: PASS "
                        + "(2 FIPS vectors, 9 MessageDigest cases, 5 equality cases, "
                        + "5 string vectors, 4 concatenations)");
    }

    private static void verifyStrings() {
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

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(String.format("%02x", value & 0xff));
        }
        return output.toString();
    }

    private static void expectNullPointer(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected API contract.
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
