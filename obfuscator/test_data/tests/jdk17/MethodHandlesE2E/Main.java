import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Main extends Base {
    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle constructor = lookup.findConstructor(
                Target.class, MethodType.methodType(void.class, String.class));
        Target target = (Target) constructor.invokeExact("created");

        MethodHandle staticMethod = lookup.findStatic(
                Target.class, "sum", MethodType.methodType(int.class, int.class, int.class));
        int sum = (int) staticMethod.invokeExact(4, 9);

        MethodHandle virtualMethod = lookup.findVirtual(
                Target.class, "join", MethodType.methodType(String.class, String.class));
        String joined = (String) virtualMethod.invokeExact(target, "-virtual");

        MethodHandle setter = lookup.findSetter(Target.class, "number", int.class);
        MethodHandle getter = lookup.findGetter(Target.class, "number", int.class);
        setter.invokeExact(target, 42);
        int fieldValue = (int) getter.invokeExact(target);

        MethodHandle special = lookup.findSpecial(
                Base.class, "message", MethodType.methodType(String.class), Main.class);
        String specialValue = (String) special.invokeExact(new Main());

        MethodHandle adapted = staticMethod.asType(
                MethodType.methodType(long.class, short.class, byte.class));
        long adaptedValue = (long) adapted.invokeExact((short) 20, (byte) 22);

        System.out.println(sum);
        System.out.println(joined);
        System.out.println(fieldValue);
        System.out.println(specialValue);
        System.out.println(adaptedValue);

        MethodHandle failure = lookup.findStatic(
                Target.class, "fail", MethodType.methodType(void.class));
        try {
            failure.invokeExact();
        } catch (IllegalArgumentException expected) {
            System.out.println(expected.getMessage());
        }
    }
}

class Base {
    protected String message() {
        return "base-special";
    }
}

class Target {
    public int number;
    private final String value;

    public Target(String value) {
        this.value = value;
    }

    public static int sum(int left, int right) {
        return left + right;
    }

    public String join(String suffix) {
        return value + suffix;
    }

    public static void fail() {
        throw new IllegalArgumentException("method-handle-failure");
    }
}
