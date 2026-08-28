public class Main {
    private static final ScopedValue<String> REQUEST = ScopedValue.newInstance();

    private static void printRequest() {
        System.out.println(REQUEST.orElse("unbound"));
    }

    public static void main(String[] args) {
        printRequest();
        ScopedValue.where(REQUEST, "outer").run(() -> {
            printRequest();
            ScopedValue.where(REQUEST, "inner").run(Main::printRequest);
            printRequest();
        });
        printRequest();
    }
}
