public class Main {
    private enum Signal {
        RED,
        AMBER,
        GREEN
    }

    private static int score(int value) {
        return switch (value) {
            case -2, -1 -> 7;
            case 0 -> 11;
            case 1, 2 -> {
                int squared = value * value;
                yield 20 + squared;
            }
            default -> -value;
        };
    }

    private static String action(Signal signal) {
        return switch (signal) {
            case RED -> "stop";
            case AMBER -> "wait";
            case GREEN -> "go";
        };
    }

    public static void main(String[] args) {
        System.out.println(score(-2));
        System.out.println(score(0));
        System.out.println(score(2));
        System.out.println(score(9));

        for (Signal signal : Signal.values()) {
            System.out.println(signal.name() + "=" + action(signal));
        }
    }
}
