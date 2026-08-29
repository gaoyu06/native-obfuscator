import java.util.Locale;

public class Main {
    private static String classify(Object value) {
        return switch (value) {
            case null -> "null";
            case String text when text.isEmpty() -> "empty-string";
            case String text -> "string:" + text.toUpperCase(Locale.ROOT);
            case Integer number when number < 0 -> "negative:" + -number;
            case Integer number -> "integer:" + number;
            case Value typed -> describe(typed);
            default -> "other:" + value.getClass().getSimpleName();
        };
    }

    private static String describe(Value value) {
        return switch (value) {
            case Text text -> "text:" + text.value();
            case Count count -> "count:" + count.value();
        };
    }

    private static String stringLength(Object value) {
        if (value instanceof String text && text.length() > 3) {
            return "length:" + text.length();
        }
        return "not-long-string";
    }

    public static void main(String[] args) {
        Object[] values = {
                null,
                "",
                "java",
                -7,
                9,
                new Text("fixture"),
                new Count(6),
                3L
        };
        for (Object value : values) {
            System.out.println(classify(value));
        }
        System.out.println(stringLength("pattern"));
        System.out.println(stringLength(21));
    }
}

sealed interface Value permits Text, Count {
}

record Text(String value) implements Value {
}

record Count(int value) implements Value {
}
