public class Main {
    private static String describe(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return "text:" + text.strip().toUpperCase();
        }
        if (value instanceof int[] numbers) {
            int sum = 0;
            for (int number : numbers) {
                sum += number;
            }
            return "array:" + numbers.length + ":" + sum;
        }
        return "other:" + value;
    }

    private static int doubledNumber(Object value) {
        if (!(value instanceof Number number)) {
            return -1;
        }
        return number.intValue() * 2;
    }

    public static void main(String[] args) {
        System.out.println(describe("  pattern  "));
        System.out.println(describe(new int[]{3, -1, 8}));
        System.out.println(describe("   "));
        System.out.println(doubledNumber(Integer.valueOf(21)));
        System.out.println(doubledNumber("21"));
    }
}
