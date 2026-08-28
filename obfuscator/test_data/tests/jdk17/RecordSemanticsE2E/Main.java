import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Point first = new Point(7, "alpha");
        Point same = new Point(7, "alpha");
        Point different = new Point(8, "beta");

        System.out.println(first.x());
        System.out.println(first.label());
        System.out.println(first.equals(same));
        System.out.println(first.equals(different));
        System.out.println(first.hashCode() == same.hashCode());
        System.out.println(first);
        System.out.println(Point.class.isRecord());

        String components = Arrays.stream(Point.class.getRecordComponents())
                .map(Main::describe)
                .collect(Collectors.joining(","));
        System.out.println(components);
    }

    private static String describe(RecordComponent component) {
        return component.getName() + ":" + component.getType().getName();
    }
}

record Point(int x, String label) {
}
