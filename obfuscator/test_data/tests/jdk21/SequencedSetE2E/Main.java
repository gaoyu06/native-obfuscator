import java.util.LinkedHashSet;
import java.util.SequencedSet;

public class Main {
    public static void main(String[] args) {
        SequencedSet<String> values =
                new LinkedHashSet<>(java.util.List.of("alpha", "beta", "gamma"));

        values.addFirst("zero");
        values.addLast("omega");

        System.out.println(values.getFirst());
        System.out.println(values.getLast());
        System.out.println(values.reversed());

        SequencedSet<String> reversed = values.reversed();
        System.out.println(reversed.removeFirst());
        reversed.addFirst("tail");

        System.out.println(values);
        System.out.println(reversed.getFirst());
        System.out.println(reversed.getLast());
        System.out.println(values.removeFirst());
        System.out.println(values.removeLast());
        System.out.println(values);
    }
}
