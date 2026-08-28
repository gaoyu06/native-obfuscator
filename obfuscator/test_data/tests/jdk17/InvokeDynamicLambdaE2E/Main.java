import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public class Main {
    private static String decorate(String value) {
        return "[" + value.toUpperCase() + "]";
    }

    public static void main(String[] args) {
        String prefix = "value=";
        int offset = 7;
        IntUnaryOperator captured = value -> value * 3 + offset;
        Function<Integer, String> describe = value -> prefix + captured.applyAsInt(value);
        Function<String, String> methodReference = Main::decorate;
        Supplier<ArrayList<String>> constructorReference = ArrayList::new;

        ArrayList<String> values = constructorReference.get();
        values.add(describe.apply(5));
        values.add(methodReference.apply("lambda"));

        System.out.println(values);
        System.out.println(captured.applyAsInt(-2));

        try {
            ((Runnable) () -> {
                throw new IllegalStateException("lambda-failure");
            }).run();
        } catch (IllegalStateException expected) {
            System.out.println(expected.getMessage());
        }
    }
}
