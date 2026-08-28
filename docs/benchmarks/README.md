# Reproducible JVM versus legacy/IR transpiled-JNI benchmark

This harness measures the legacy and IR code generators against plain Java.
It does not claim that either transpiled mode is faster.

## Run

Prerequisites:

- a JDK capable of compiling Java 8 bytecode;
- Python 3;
- CMake;
- a working C++17 compiler and JNI headers.

From the repository root:

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:bench
CC=gcc CXX=g++ ./gradlew :obfuscator:benchIr
```

`bench` selects `--codegen=legacy`; `benchIr` selects `--codegen=ir`.
Both tasks run the plain JVM reference before the selected native mode.
They are intentionally not dependencies of `check` or `test`. GCC is
recommended because the generated native project must link libstdc++.

The harness prints JSON and writes the identical report to:

```text
build/benchmarks/results-legacy.json
build/benchmarks/results-ir.json
```

`build/benchmarks/results.json` is also updated with the most recent mode.
Transpiler stdout/stderr is retained in
`build/benchmarks/logs/transpile-{legacy,ir}.log`.

Warmup and measurement counts may be increased through environment variables:

```sh
BENCH_WARMUP=10 BENCH_ITERATIONS=20 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:bench
BENCH_WARMUP=10 BENCH_ITERATIONS=20 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:benchIr
```

At least one warmup and two measurement iterations are enforced.

## What runs

Gradle builds one Java 8 benchmark JAR and the transpiler fat JAR. The Python
driver then:

1. runs the unmodified benchmark JAR on the JVM;
2. transpiles only `benchmarks/kernels/**` from that same JAR with the
   task-selected code generator;
3. verifies every kernel method against generated `// IR codegen:` markers or
   the transpiler's per-method legacy-fallback logs;
4. configures and compiles the generated C++ shared library with CMake;
5. runs the transformed JAR with that shared library;
6. rejects unclassified methods, mismatched kernel sets, or checksums.

Every measured sample uses `System.nanoTime()`. An untimed checksum preflight
and warmup iterations precede the recorded samples. A volatile sink consumes
each result. The report retains every sample and computes mean and median from
only those recorded samples.

The kernels are:

- `ir-friendly-int-loop`: 5,000,000 iterations of deterministic, `int`-only
  arithmetic supported by the current IR subset;
- `integer-loop`: 5,000,000 iterations of deterministic integer arithmetic;
- `string-concat-hash`: 200 calls of 96 concatenations plus `String.hashCode`;
- `recursion`: 2,000 recursive traversals at depth 32.

The work is batched inside each measured sample. In the string case, calls are
split so the current JNI generator can release local references between calls.
The timings therefore represent these exact workloads, including the current
Java/native call boundaries; they are not normalized algorithm-only costs.

## Failure behavior

Native work is never silently skipped. A missing tool, transpilation error,
CMake error, compiler error, native execution error, timeout, invalid JSON, or
checksum mismatch produces a top-level `FAIL`, records the stage and command,
marks the native result `FAIL`, writes the partial report, and makes the Gradle
task fail. In IR mode, a missing IR marker/fallback record fails before native
compilation. No native timings are emitted unless the transformed JAR actually
runs successfully.

## Interpreting results

This is a small custom runner rather than JMH so the project retains its Java 8
main compilation and adds no benchmark dependency. It provides warmup, repeated
samples, raw values, median, and mean, but not process forks or confidence
intervals. Treat a single run as local diagnostic evidence, not a release
threshold or a general performance claim. Compare repeated runs on controlled
hardware before drawing conclusions.

The checked-in IR-versus-legacy run is in
[`results-ir-vs-legacy.md`](results-ir-vs-legacy.md).
