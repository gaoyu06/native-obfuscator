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
}
