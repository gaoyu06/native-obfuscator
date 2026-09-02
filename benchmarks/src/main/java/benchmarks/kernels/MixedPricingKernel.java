package benchmarks.kernels;

/**
 * Mixed control flow for regression, not a speedup kernel.
 * One native entry walks objects, int fields, an {@code int[]} tax table,
 * branches, {@code String.hashCode}/{@code length}, and a helper invoke.
 */
public final class MixedPricingKernel {
    private static Line[] catalog;
    private static int[] taxBps;

    private MixedPricingKernel() {
    }

    public static long run(int passes) {
        Line[] lines = catalog();
        int[] taxes = taxes();
        Totals totals = new Totals();
        long checksum = 0;
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < lines.length; i++) {
                Line line = lines[i];
                int quantity = line.quantity;
                int unit = line.unitCents;
                int subtotal = quantity * unit;
                if (quantity >= 10) {
                    subtotal -= subtotal / 10;
                    totals.discounted++;
                }
                int tax = taxes[i % taxes.length];
                subtotal += (int) ((long) subtotal * tax / 10000L);
                totals.lines++;
                checksum += subtotal;
                checksum += mix(line);
                if ((i & 7) == 0) {
                    checksum += line.sku.length();
                }
            }
        }
        return checksum + totals.lines + ((long) totals.discounted << 32);
    }

    public static int priceOne(int quantity, int unitCents, int taxBps, String sku) {
        int subtotal = quantity * unitCents;
        if (quantity >= 10) {
            subtotal -= subtotal / 10;
        }
        subtotal += (int) ((long) subtotal * taxBps / 10000L);
        return subtotal + sku.hashCode();
    }

    private static int mix(Line line) {
        return line.sku.hashCode() ^ line.quantity ^ line.unitCents;
    }

    private static Line[] catalog() {
        Line[] lines = catalog;
        if (lines != null) {
            return lines;
        }
        lines = new Line[128];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new Line("SKU-" + i, 1 + (i % 20), 99 + (i * 3));
        }
        catalog = lines;
        return lines;
    }

    private static int[] taxes() {
        int[] taxes = taxBps;
        if (taxes != null) {
            return taxes;
        }
        taxes = new int[]{0, 500, 1000, 1300};
        taxBps = taxes;
        return taxes;
    }

    public static final class Line {
        public final String sku;
        public int quantity;
        public int unitCents;

        public Line(String sku, int quantity, int unitCents) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitCents = unitCents;
        }
    }

    public static final class Totals {
        public int lines;
        public int discounted;
    }
}
