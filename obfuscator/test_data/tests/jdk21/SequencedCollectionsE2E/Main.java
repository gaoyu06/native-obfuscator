import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

public class Main {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        values.addFirst("zero");
        values.addLast("omega");

        System.out.println(values.getFirst());
        System.out.println(values.getLast());
        System.out.println(values.reversed());
        System.out.println(values.removeFirst());
        System.out.println(values.removeLast());
        System.out.println(values);

        SequencedMap<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.putFirst(0, "zero");
        map.putLast(3, "three");

        System.out.println(map.firstEntry());
        System.out.println(map.lastEntry());
        System.out.println(map.sequencedKeySet().reversed());
        System.out.println(map.reversed());
    }
}
