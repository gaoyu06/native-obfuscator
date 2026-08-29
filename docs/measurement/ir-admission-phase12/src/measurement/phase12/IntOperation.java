package measurement.phase12;

public interface IntOperation {
    default int apply(int value) {
        return value + 3;
    }
}
