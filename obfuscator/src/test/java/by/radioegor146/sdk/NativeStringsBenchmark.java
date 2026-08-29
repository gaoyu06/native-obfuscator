/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

import java.util.Arrays;

/**
 * Small, dependency-free measurement used by the native SDK integration test.
 *
 * <p>This is diagnostic evidence rather than a production performance gate.</p>
 */
public final class NativeStringsBenchmark {
    private static final String[] PARTS = {
            "alpha", "\u03B2eta", "\u4E2D\u6587", "\uD83D\uDE00",
            "-", "native", "jvm", "\u0000"
    };
    private static volatile long sink;

    private NativeStringsBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: NativeStringsBenchmark <java|native> <warmup> <iterations>");
        }
        boolean useNative;
        if ("java".equals(args[0])) {
            useNative = false;
        } else if ("native".equals(args[0])) {
            useNative = true;
        } else {
            throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }

        int warmup = positive(args[1], "warmup");
        int iterations = positive(args[2], "iterations");
        long expected = consume(runKernel(useNative));
        for (int i = 0; i < warmup; i++) {
            check(expected, consume(runKernel(useNative)));
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            long actual = consume(runKernel(useNative));
            samples[i] = System.nanoTime() - start;
            check(expected, actual);
        }

        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double median = sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                : sorted[sorted.length / 2];
        System.out.println(
                "NativeStringsBenchmark: mode=" + args[0]
                        + " checksum=" + expected
                        + " median_ns=" + median
                        + " samples_ns=" + Arrays.toString(samples));
    }

    private static long runKernel(boolean useNative) {
        long result = 0;
        for (int call = 0; call < 127; call++) {
            result ^= useNative ? runNative(256) : runJava(256);
        }
        return result;
    }

    private static long runJava(int rounds) {
        String value = "";
        long checksum = 0;
        for (int i = 0; i < rounds; i++) {
            value = value.concat(PARTS[i & 7]);
            int length = value.length();
            int hash = value.hashCode();
            checksum = updateChecksum(checksum, hash, length, i);
            if (length >= 96) {
                value = "";
            }
        }
        return updateChecksum(checksum, value.hashCode(), value.length(), 0);
    }

    private static long runNative(int rounds) {
        String value = "";
        long checksum = 0;
        for (int i = 0; i < rounds; i++) {
            value = NativeStrings.concat(value, PARTS[i & 7]);
            int length = NativeStrings.length(value);
            int hash = NativeStrings.hashCode(value);
            checksum = updateChecksum(checksum, hash, length, i);
            if (length >= 96) {
                value = "";
            }
        }
        return updateChecksum(
                checksum,
                NativeStrings.hashCode(value),
                NativeStrings.length(value),
                0);
    }

    private static long updateChecksum(long checksum, int hash, int length, int index) {
        return (checksum + (hash & 0xffffffffL) + length + index) % 1_000_000_007L;
    }

    private static long consume(long value) {
        sink = value;
        return sink;
    }

    private static void check(long expected, long actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "String benchmark checksum changed: expected "
                            + expected + ", got " + actual);
        }
    }

    private static int positive(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }
}
