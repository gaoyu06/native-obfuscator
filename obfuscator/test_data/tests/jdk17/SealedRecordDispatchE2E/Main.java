public class Main {
    private static String describe(Token token) {
        if (token instanceof NumberToken number) {
            return "number:" + number.value() + ":" + number.weight();
        }
        if (token instanceof WordToken word) {
            return "word:" + word.value() + ":" + word.weight();
        }
        throw new IllegalStateException("unexpected token");
    }

    private static int totalWeight(Token[] tokens) {
        int result = 0;
        for (Token token : tokens) {
            result += token.weight();
        }
        return result;
    }

    public static void main(String[] args) {
        Token[] tokens = {
                new NumberToken(6),
                new WordToken("sealed"),
                new NumberToken(-2)
        };

        for (Token token : tokens) {
            System.out.println(describe(token));
        }
        System.out.println("total=" + totalWeight(tokens));
    }
}

sealed interface Token permits NumberToken, WordToken {
    int weight();
}

record NumberToken(int value) implements Token {
    @Override
    public int weight() {
        return value * 2;
    }
}

record WordToken(String value) implements Token {
    @Override
    public int weight() {
        return value.length();
    }
}
