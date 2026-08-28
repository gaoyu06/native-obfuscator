package benchmarks.kernels;

public final class StringConcatHashKernel {
    private StringConcatHashKernel() {
    }

    public static int run(int rounds) {
        String value = "";
        int hash = 1;
        for (int i = 0; i < rounds; i++) {
            value = value + (char) ('a' + (i & 15));
            if (value.length() == 32) {
                hash = 31 * hash + value.hashCode();
                value = "";
            }
        }
        return 31 * hash + value.hashCode();
    }
}
