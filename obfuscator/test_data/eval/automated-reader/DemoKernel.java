public final class DemoKernel {
    private DemoKernel() {
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static int sumTo(int limit) {
        int acc = 0;
        for (int value = 0; value < limit; value++) {
            acc += value;
        }
        return acc;
    }

    public static int mix(int seed, int rounds) {
        int acc = seed ^ 0x9E3779B9;
        for (int r = 0; r < rounds; r++) {
            acc += (acc << 6) + (acc >>> 2);
            acc ^= acc * 0x85EBCA77;
            acc = Integer.rotateLeft(acc, 13);
        }
        return acc;
    }
}
