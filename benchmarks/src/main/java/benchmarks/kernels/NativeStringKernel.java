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
            checksum = (checksum + (hash & 0xffffffffL) + length + i)
                    % 1_000_000_007L;
            if (length >= 96) {
                value = "";
            }
        }
        return (checksum
                + (NativeStrings.hashCode(value) & 0xffffffffL)
                + NativeStrings.length(value))
                % 1_000_000_007L;
    }
}
