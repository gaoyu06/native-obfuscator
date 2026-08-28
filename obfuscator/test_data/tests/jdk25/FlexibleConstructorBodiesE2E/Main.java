public class Main {
    private static class Base {
        private final int value;

        Base(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }
    }

    private static final class Validated extends Base {
        Validated(int value) {
            int normalized = Math.abs(value);
            if (normalized == 0) {
                throw new IllegalArgumentException("zero");
            }
            super(normalized);
        }
    }

    private static final class Delegating extends Base {
        Delegating(String value) {
            int parsed = Integer.parseInt(value);
            this(parsed);
        }

        Delegating(int value) {
            super(value * 2);
        }
    }

    public static void main(String[] args) {
        System.out.println(new Validated(-12).value());
        System.out.println(new Delegating("7").value());

        try {
            new Validated(0);
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
    }
}
