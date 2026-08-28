import java.lang.reflect.Method;

public final class BlindedRunner {
    public static void main(String[] args) throws Exception {
        Class<?> kernel = Class.forName("DemoKernel");
        Method add = kernel.getDeclaredMethod("add", int.class, int.class);
        Method sumTo = kernel.getDeclaredMethod("sumTo", int.class);
        Method mix = kernel.getDeclaredMethod("mix", int.class, int.class);
        Method divide = kernel.getDeclaredMethod("divide", int.class, int.class);

        System.out.println(add.invoke(null, 7, -3));
        System.out.println(sumTo.invoke(null, 10));
        System.out.println(mix.invoke(null, 0x12345678, 5));
        System.out.println(divide.invoke(null, 81, 9));
    }
}
