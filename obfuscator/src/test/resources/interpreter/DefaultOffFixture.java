public final class DefaultOffFixture {
    private DefaultOffFixture() {
    }

    public static int add(int left, int right) {
        return left + right;
    }

    public static int sumTo(int limit) {
        int sum = 0;
        for (int value = 0; value < limit; value++) {
            sum += value;
        }
        return sum;
    }

    public static int multiply(int left, int right) {
        return left * right;
    }

    public static int bitwise(int left, int right) {
        return (left & right) ^ (left | right);
    }

    public static int shift(int value, int distance) {
        return (value << distance) ^ (value >> distance) ^
                (value >>> distance);
    }

    public static int unsupportedConversion(int value) {
        return (int) (long) value;
    }
}
