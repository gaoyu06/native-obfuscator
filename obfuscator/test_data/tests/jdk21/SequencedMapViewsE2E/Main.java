import java.util.LinkedHashMap;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;

public class Main {
    public static void main(String[] args) {
        SequencedMap<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");
        map.putFirst(0, "zero");
        map.putLast(4, "four");

        System.out.println(map.pollFirstEntry());
        System.out.println(map.pollLastEntry());

        SequencedSet<Integer> keys = map.sequencedKeySet();
        SequencedCollection<String> values = map.sequencedValues();
        System.out.println(keys.getFirst() + ":" + keys.getLast());
        System.out.println(keys.reversed());
        System.out.println(values.getFirst() + ":" + values.getLast());
        System.out.println(values.reversed());

        SequencedMap<Integer, String> reversed = map.reversed();
        System.out.println(reversed.pollFirstEntry());
        System.out.println(reversed.pollLastEntry());
        System.out.println(map);
        System.out.println(reversed);
    }
}
