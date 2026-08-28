package by.radioegor146.compiletime;

public class LoaderPlain {
    public static native void registerNativesForClass(int index, Class<?> clazz);

    public static void load() {
    }

    static {
        System.loadLibrary("%LIB_NAME%");
    }
}
