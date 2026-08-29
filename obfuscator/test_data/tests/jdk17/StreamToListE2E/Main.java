import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static List<Integer> normalizedLengths(List<String> values) {
        return values.stream()
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .map(String::length)
                .sorted()
                .toList();
    }

    public static void main(String[] args) {
        List<Integer> lengths = normalizedLengths(
                List.of("bbb", " a ", "cc", "dddd", "  ", "ee"));
        System.out.println(lengths);

        try {
            lengths.add(99);
            System.out.println("mutable");
        } catch (UnsupportedOperationException expected) {
            System.out.println("unmodifiable");
        }

        List<String> withNull = Stream.of("left", null, "right").toList();
        System.out.println(withNull);
        System.out.println(withNull.contains(null));
    }
}
