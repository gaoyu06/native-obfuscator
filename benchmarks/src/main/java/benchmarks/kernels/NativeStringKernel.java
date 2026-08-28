package benchmarks.kernels;

import by.radioegor146.sdk.NativeStrings;

public final class NativeStringKernel {
    private static final String[] PARTS = {
            "alpha", "\u03B2eta", "\u4E2D\u6587", "\uD83D\uDE00",
            "-", "native", "jvm", "\u0000"
    };

    private NativeStringKernel() {
    }

    public static long run(int rounds) {
        String value = "";
        long checksum = 0;
        for (int i = 0; i < rounds; i++) {
            value = NativeStrings.concat(value, PARTS[i & 7]);
            int length = NativeStrings.length(value);
            int hash = NativeStrings.hashCode(value);
            checksum ^= ((long) length << 32) ^ (hash & 0xffffffffL) ^ i;
            if (length >= 96) {
                value = "";
            }
        }
        return checksum
                ^ ((long) NativeStrings.length(value) << 32)
                ^ (NativeStrings.hashCode(value) & 0xffffffffL);
    }
}
