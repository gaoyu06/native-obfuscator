# Reproducible native string fast-path benchmark

This harness compares plain Java string operations, the explicit native string
SDK, and the current per-bytecode JNI transpiler path.

## Run

Prerequisites:

- a JDK capable of compiling Java 8 bytecode;
- Python 3;
- CMake;
- a working C++17 compiler and JNI headers.

From the repository root:

```sh
./gradlew :obfuscator:bench
```

`./gradlew bench` is also a Gradle task selector for the same task in this
repository. The task is intentionally not a dependency of `check` or `test`.

The harness prints JSON and writes the identical report to:

```text
build/benchmarks/results.json
```

Warmup and measurement counts may be increased through environment variables:

```sh
BENCH_WARMUP=10 BENCH_ITERATIONS=20 ./gradlew :obfuscator:bench
```

At least one warmup and two measurement iterations are enforced.

## What runs

Gradle builds one Java 8 benchmark JAR and the transpiler fat JAR. The Python
driver then:

1. runs the unmodified Java string kernel on the JVM;
2. transpiles only `benchmarks/kernels/JavaStringKernel**` from that same JAR;
3. configures and compiles the generated C++ shared library with CMake;
4. runs the untransformed SDK kernel against `NativeStrings`;
5. runs the transformed Java kernel through the existing snippet/JNI path;
6. rejects mismatched kernel sets or checksums.

Every measured sample uses `System.nanoTime()`. An untimed checksum preflight
and warmup iterations precede the recorded samples. A volatile sink consumes
each result. The report retains every sample and computes mean and median from
only those recorded samples.

The `string-length-hash-concat` kernel performs 127 calls of 256 iterations.
Each iteration concatenates two strings, reads the UTF-16 length, and computes
the Java-compatible hash. Inputs include ASCII, BMP Unicode, a surrogate pair,
and an embedded NUL. The timings represent this exact workload, including each
mode's Java/native boundaries; they are not normalized algorithm-only costs.

## Failure behavior

Native work is never silently skipped. A missing tool, transpilation error,
CMake error, compiler error, native execution error, timeout, invalid JSON, or
checksum mismatch produces a top-level `FAIL`, records the stage and command,
marks the native result `FAIL`, writes the partial report, and makes the Gradle
task fail. No native timings are emitted unless the transformed JAR actually
runs successfully.

## Interpreting results

This is a small custom runner rather than JMH so the project retains its Java 8
main compilation and adds no benchmark dependency. It provides warmup, repeated
samples, raw values, median, and mean, but not process forks or confidence
intervals. Treat a single run as local diagnostic evidence, not a release
threshold or a general performance claim. Compare repeated runs on controlled
hardware before drawing conclusions.

The string fast-path run is recorded in
[`string-fastpath-results.md`](string-fastpath-results.md). The older general
generator baseline remains in [`results-local.md`](results-local.md).
