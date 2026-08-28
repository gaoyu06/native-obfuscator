package benchmarks.kernels;

public final class IrFriendlyIntKernel {
    private IrFriendlyIntKernel() {
    }

    public static int run(int rounds) {
        int value = 0x1234ABCD;
        for (int i = 0; i < rounds; i++) {
            value = (value * 1664525 + 1013904223) ^ (value >>> 13);
            value += (i * 31) ^ (value << 5);
        }
        return value;
    }
}
