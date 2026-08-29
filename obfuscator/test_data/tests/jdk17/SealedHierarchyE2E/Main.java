import java.util.Arrays;
import java.util.stream.Collectors;

public class Main {
    private static int area(Shape shape) {
        return shape.area();
    }

    public static void main(String[] args) {
        Shape circle = new Circle(3);
        Shape rectangle = new Rectangle(4, 5);

        System.out.println(area(circle));
        System.out.println(area(rectangle));
        System.out.println(circle.kind());
        System.out.println(rectangle.kind());
        System.out.println(Shape.class.isSealed());

        String permitted = Arrays.stream(Shape.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.joining(","));
        System.out.println(permitted);
    }
}

sealed interface Shape permits Circle, Rectangle {
    int area();

    default String kind() {
        return getClass().getSimpleName();
    }
}

final class Circle implements Shape {
    private final int radius;

    Circle(int radius) {
        this.radius = radius;
    }

    public int area() {
        return radius * radius;
    }
}

final class Rectangle implements Shape {
    private final int width;
    private final int height;

    Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int area() {
        return width * height;
    }
}
