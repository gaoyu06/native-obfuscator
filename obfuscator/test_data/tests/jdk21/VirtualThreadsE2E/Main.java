import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    private static int checksum(int limit) {
        int result = 0;
        for (int value = 1; value <= limit; value++) {
            result = result * 31 + value;
        }
        return result;
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger result = new AtomicInteger();
        Thread worker = Thread.startVirtualThread(() -> result.set(checksum(7)));
        worker.join();

        System.out.println(worker.isVirtual());
        System.out.println(worker.getState());
        System.out.println(result.get());

        AtomicReference<String> workerName = new AtomicReference<>();
        Thread named = Thread.ofVirtual()
                .name("fixture-", 0)
                .unstarted(() -> workerName.set(Thread.currentThread().getName()));
        named.start();
        named.join();

        System.out.println(named.isVirtual());
        System.out.println(workerName.get());
    }
}
