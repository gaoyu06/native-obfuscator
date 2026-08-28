package example;

/**
 * Builder-only fixture source. It is retained solely to reproduce published.jar
 * and is not reader/recovery evidence.
 */
public final class Math {
    private Math() {
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static int sumTo(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    public static int subMul(int a, int b) {
        return (a - b) * b;
    }

    public static int mix(int a, int b) {
        int x = a * 0x045d9f3b;
        int y = b * 0x119de1f3;
        x ^= x >>> 16;
        y ^= y << 7;
        int z = (x + y) ^ (a << 5);
        z = z * 33 + (b >>> 3);
        return z ^ (a & b);
    }
}
