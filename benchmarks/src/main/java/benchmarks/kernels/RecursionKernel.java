package benchmarks.kernels;

public final class RecursionKernel {
    private RecursionKernel() {
    }

    public static long run(int repetitions, int depth) {
        long result = 0;
        for (int i = 0; i < repetitions; i++) {
            result += recurse(depth, i);
        }
        return result;
    }

    private static long recurse(int depth, long value) {
        if (depth == 0) {
            return value;
        }
        return recurse(depth - 1, value + depth) ^ depth;
    }
}
