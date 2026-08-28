public final class Runner {
    private Runner() {
    }

    public static void main(String[] args) {
        System.out.println("add(7,5)=" + DemoKernel.add(7, 5));
        System.out.println("sumTo(0)=" + DemoKernel.sumTo(0));
        System.out.println("sumTo(1)=" + DemoKernel.sumTo(1));
        System.out.println("sumTo(10)=" + DemoKernel.sumTo(10));
        System.out.println("sumTo(100)=" + DemoKernel.sumTo(100));
        System.out.println("mix(0,0)=" + DemoKernel.mix(0, 0));
        System.out.println("mix(0,1)=" + DemoKernel.mix(0, 1));
        System.out.println("mix(1,4)=" + DemoKernel.mix(1, 4));
        System.out.println("mix(MIN,3)=" + DemoKernel.mix(Integer.MIN_VALUE, 3));
        System.out.println("mix(0x12345678,16)=" + DemoKernel.mix(0x12345678, 16));
    }
}
