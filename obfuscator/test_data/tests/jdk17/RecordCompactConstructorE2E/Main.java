public class Main {
    public static void main(String[] args) {
        Interval interval = new Interval(9, 3);
        System.out.println(interval.start() + ":" + interval.end());
        System.out.println(interval.width());
        System.out.println(interval.inclusiveSum());
        System.out.println(interval);

        try {
            new Interval(-1, 4);
            System.out.println("accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println(expected.getMessage());
        }
    }
}

record Interval(int start, int end) {
    Interval {
        if (start > end) {
            int temporary = start;
            start = end;
            end = temporary;
        }
        if (start < 0) {
            throw new IllegalArgumentException("negative-start:" + start);
        }
    }

    int width() {
        return end - start;
    }

    int inclusiveSum() {
        int result = 0;
        for (int value = start; value <= end; value++) {
            result += value;
        }
        return result;
    }
}
