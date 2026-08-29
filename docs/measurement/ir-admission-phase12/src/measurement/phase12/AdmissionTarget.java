package measurement.phase12;

public final class AdmissionTarget {
    private int count;
    private long total;
    private IntOperation operation;

    public AdmissionTarget(int count, long total, IntOperation operation) {
        this.count = count;
        this.total = total;
        this.operation = operation;
    }

    public int increment(int delta) {
        count += delta;
        return count;
    }

    public long total() {
        return total;
    }

    public int call(int value) {
        return operation.apply(value);
    }

    public int unsupported(int value) {
        return (int) (value + 0.5f);
    }
}
