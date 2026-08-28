package benchmarks.kernels;

public final class JavaStringKernel {
    private static final String[] PARTS = {
            "alpha", "\u03B2eta", "\u4E2D\u6587", "\uD83D\uDE00",
            "-", "native", "jvm", "\u0000"
    };

    private JavaStringKernel() {
    }

    public static long run(int rounds) {
        String value = "";
        long checksum = 0;
        for (int i = 0; i < rounds; i++) {
            value = value.concat(PARTS[i & 7]);
            int length = value.length();
            int hash = value.hashCode();
            checksum ^= ((long) length << 32) ^ (hash & 0xffffffffL) ^ i;
            if (length >= 96) {
                value = "";
            }
        }
        return checksum
                ^ ((long) value.length() << 32)
                ^ (value.hashCode() & 0xffffffffL);
    }
}
