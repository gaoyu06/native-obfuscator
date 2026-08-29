public class Main {
    private static int checksum(String value) {
        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            result = result * 31 + value.charAt(index);
        }
        return result;
    }

    public static void main(String[] args) {
        String document = """
                alpha
                  beta
                gamma\
                -delta\s
                """;

        System.out.println(document.replace(" ", "_").replace("\n", "|"));
        System.out.println(document.lines().count());
        System.out.println(document.length());
        System.out.println(checksum(document));
    }
}
