public class Main {
    private static String inspect(Object value) {
        return switch (value) {
            case null -> "null";
            case Box(Point(int x, int y)) when x == y -> "square:" + x;
            case Box(Point(int x, int y)) -> "point:" + x + "," + y;
            case Pair(Box(Point(int ax, int ay)), Box(Point(int bx, int by))) ->
                    "pair:" + (ax + ay) + ":" + (bx + by);
            default -> "other";
        };
    }

    private static int coordinateSum(Object value) {
        if (value instanceof Box(Point(var x, var y))) {
            return x + y;
        }
        return -1;
    }

    public static void main(String[] args) {
        Box square = new Box(new Point(4, 4));
        Box rectangle = new Box(new Point(3, 8));
        Pair pair = new Pair(square, rectangle);

        System.out.println(inspect(null));
        System.out.println(inspect(square));
        System.out.println(inspect(rectangle));
        System.out.println(inspect(pair));
        System.out.println(inspect("record-pattern"));
        System.out.println(coordinateSum(rectangle));
        System.out.println(coordinateSum(pair));
        System.out.println(pair);
    }
}

record Point(int x, int y) {
}

record Box(Point point) {
}

record Pair(Box first, Box second) {
}
