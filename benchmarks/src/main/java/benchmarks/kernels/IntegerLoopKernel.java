package benchmarks.kernels;

public final class IntegerLoopKernel {
    private IntegerLoopKernel() {
    }

    public static long run(int rounds) {
        long value = 0x1234ABCDL;
        for (int i = 0; i < rounds; i++) {
            value = (value * 1664525L + 1013904223L) ^ (value >>> 17);
            value += ((long) i * 31L) ^ (value << 7);
        }
        return value;
    }
}
