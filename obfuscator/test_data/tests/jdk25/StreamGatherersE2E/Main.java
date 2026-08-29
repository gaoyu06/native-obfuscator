import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class Main {
    private static List<List<Integer>> fixedWindows() {
        return Stream.of(1, 2, 3, 4, 5, 6, 7)
                .gather(Gatherers.windowFixed(3))
                .toList();
    }

    private static List<Integer> runningTotals() {
        return Stream.of(2, 3, 5, 7)
                .gather(Gatherers.scan(() -> 0, Integer::sum))
                .toList();
    }

    private static int foldedTotal() {
        return Stream.of(11, 13, 17)
                .gather(Gatherers.fold(() -> 0, Integer::sum))
                .findFirst()
                .orElseThrow();
    }

    public static void main(String[] args) {
        System.out.println(fixedWindows());
        System.out.println(runningTotals());
        System.out.println(foldedTotal());
    }
}
