import java.util.Arrays;
import java.util.stream.Collectors;

public class Main {
    private int value = 10;

    private String secret(int increment) {
        value += increment;
        return "secret=" + value;
    }

    static class Worker {
        static String access(Main host) {
            host.value *= 2;
            return host.secret(5);
        }
    }

    public static void main(String[] args) {
        Main host = new Main();

        System.out.println(Worker.access(host));
        System.out.println(host.value);
        System.out.println(Worker.class.getNestHost().getName());
        System.out.println(Main.class.isNestmateOf(Worker.class));

        String members = Arrays.stream(Main.class.getNestMembers())
                .map(Class::getName)
                .sorted()
                .collect(Collectors.joining(","));
        System.out.println(members);
    }
}
