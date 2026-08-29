package example;

/**
 * Builder-only oracle/native runner. Calls are printed so every computed result
 * escapes into observable stdout.
 */
public final class Runner {
    private Runner() {
    }

    public static void main(String[] args) {
        System.out.println("add=" + Math.add(17, -9));
        System.out.println("sumTo=" + Math.sumTo(10));
        System.out.println("subMul=" + Math.subMul(19, 6));

        System.out.println("mix#1=" + Math.mix(0, 0));
        System.out.println("mix#2=" + Math.mix(0, 1));
        System.out.println("mix#3=" + Math.mix(0, -1));
        System.out.println("mix#4=" + Math.mix(1, 0));
        System.out.println("mix#5=" + Math.mix(-7, 0));
        System.out.println("mix#6=" + Math.mix(1, 1));
    }
}
