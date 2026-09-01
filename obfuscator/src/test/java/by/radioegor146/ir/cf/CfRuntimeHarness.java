package by.radioegor146.ir.cf;

/**
 * Plain Java oracle for native control-flow obfuscation parity.
 */
public final class CfRuntimeHarness {
    private CfRuntimeHarness() {
    }

    public static int join(boolean takeThen) {
        int value = 0;
        if (takeThen) {
            value = 1;
        }
        return value;
    }

    public static int countdown(int n) {
        int sum = 0;
        while (n > 0) {
            sum += n;
            n--;
        }
        return sum;
    }

    public static int divide(int divisor) {
        try {
            return 100 / divisor;
        } catch (ArithmeticException ignored) {
            return -1;
        }
    }

    public static void main(String[] args) {
        System.out.println(join(true));
        System.out.println(join(false));
        System.out.println(countdown(5));
        System.out.println(divide(2));
        System.out.println(divide(0));
    }
}
