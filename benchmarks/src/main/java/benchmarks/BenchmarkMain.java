package benchmarks;

import benchmarks.kernels.IntegerLoopKernel;
import benchmarks.kernels.IrFriendlyIntKernel;
import benchmarks.kernels.RecursionKernel;
import benchmarks.kernels.StringConcatHashKernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BenchmarkMain {
    private static volatile long sink;

    private BenchmarkMain() {
    }

    public static void main(String[] args) {
        String mode = option(args, "--mode", "unspecified");
        String kernelSelection = option(args, "--kernel", "all");
        int warmup = positiveInt(option(args, "--warmup", "5"), "warmup");
        int iterations = positiveInt(option(args, "--iterations", "10"), "iterations");

        KernelSpec[] availableKernels = new KernelSpec[]{
                new KernelSpec("ir-friendly-int-loop", "5,000,000 int-only loop iterations") {
                    @Override
                    long call() {
                        return IrFriendlyIntKernel.run(5_000_000);
                    }
                },
                new KernelSpec("integer-loop", "5,000,000 loop iterations") {
                    @Override
                    long call() {
                        return IntegerLoopKernel.run(5_000_000);
                    }
                },
                new KernelSpec("string-concat-hash", "200 calls x 96 concatenations") {
                    @Override
                    long call() {
                        long result = 0;
                        for (int i = 0; i < 200; i++) {
                            result += StringConcatHashKernel.run(96);
                        }
                        return result;
                    }
                },
                new KernelSpec("recursion", "2,000 traversals x depth 32") {
                    @Override
                    long call() {
                        return RecursionKernel.run(2_000, 32);
                    }
                }
        };
        List<KernelSpec> selectedKernels = new ArrayList<>();
        for (KernelSpec kernel : availableKernels) {
            if ("all".equals(kernelSelection) || kernel.name.equals(kernelSelection)) {
                selectedKernels.add(kernel);
            }
        }
        if (selectedKernels.isEmpty()) {
            throw new IllegalArgumentException("unknown kernel: " + kernelSelection);
        }

        KernelResult[] results = new KernelResult[selectedKernels.size()];
        for (int i = 0; i < selectedKernels.size(); i++) {
            results[i] = measure(selectedKernels.get(i), warmup, iterations);
        }
        printJson(mode, warmup, iterations, results);
    }

    private static KernelResult measure(KernelSpec kernel, int warmup, int iterations) {
        long expected = consume(kernel.call());
        for (int i = 0; i < warmup; i++) {
            check(kernel, expected, consume(kernel.call()));
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            long actual = consume(kernel.call());
            samples[i] = System.nanoTime() - start;
            check(kernel, expected, actual);
        }
        return new KernelResult(kernel.name, kernel.workload, samples, expected);
    }

    private static long consume(long value) {
        sink = value;
        return sink;
    }

    private static void check(KernelSpec kernel, long expected, long actual) {
        if (actual != expected) {
            throw new IllegalStateException(kernel.name + " checksum changed: expected " +
                    expected + ", got " + actual);
        }
    }

    private static String option(String[] args, String name, String fallback) {
        String prefix = name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }

    private static int positiveInt(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static void printJson(String mode, int warmup, int iterations, KernelResult[] results) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        field(out, "mode", mode).append(',');
        out.append("\"warmup\":").append(warmup).append(',');
        out.append("\"iterations\":").append(iterations).append(',');
        out.append("\"timer\":\"System.nanoTime\",");
        out.append("\"kernels\":[");
        for (int i = 0; i < results.length; i++) {
            if (i != 0) {
                out.append(',');
            }
            results[i].appendJson(out);
        }
        out.append("]}");
        System.out.println(out);
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(escape(name)).append("\":\"")
                .append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private abstract static class KernelSpec {
        final String name;
        final String workload;

        KernelSpec(String name, String workload) {
            this.name = name;
            this.workload = workload;
        }

        abstract long call();
    }

    private static final class KernelResult {
        final String name;
        final String workload;
        final long[] samples;
        final long checksum;

        KernelResult(String name, String workload, long[] samples, long checksum) {
            this.name = name;
            this.workload = workload;
            this.samples = samples;
            this.checksum = checksum;
        }

        void appendJson(StringBuilder out) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            double median = sorted.length % 2 == 0
                    ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                    : sorted[sorted.length / 2];
            double total = 0;
            for (long sample : samples) {
                total += sample;
            }

            out.append('{');
            field(out, "name", name).append(',');
            field(out, "workload", workload).append(',');
            field(out, "unit", "ns/sample").append(',');
            out.append("\"checksum\":").append(checksum).append(',');
            out.append("\"median\":").append(median).append(',');
            out.append("\"mean\":").append(total / samples.length).append(',');
            out.append("\"samples\":[");
            for (int i = 0; i < samples.length; i++) {
                if (i != 0) {
                    out.append(',');
                }
                out.append(samples[i]);
            }
            out.append("]}");
        }
    }
}
