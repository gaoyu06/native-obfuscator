# Reproducible JVM versus IR transpiled-JNI benchmark

This harness measures the IR generator against plain Java.
It does not claim that the transpiled mode is faster. One
`:obfuscator:bench` run executes plain JVM and `--codegen=ir`.
`--ir-lower=eval` exists on `master` as default-off and is **not** part of
this harness. `--codegen=legacy` is gone.
Do not invent numbers. Do not back-fill the #53 eval median (`N/A`).
Current product status:
[../architecture/project-status.md](../architecture/project-status.md).

## Run

Prerequisites:

- a JDK capable of compiling Java 8 bytecode;
- Python 3;
- CMake;
- a working C++17 compiler and JNI headers.

From the repository root:

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:bench
```

`./gradlew bench` is also a Gradle task selector for the same task in this
repository. One invocation runs plain JVM, then `--codegen=ir`. The task is
intentionally not a dependency of `check` or `test`.

The harness prints JSON and writes the identical report to:

```text
build/benchmarks/results.json
```

Warmup and measurement counts may be increased through environment variables:

```sh
BENCH_WARMUP=10 BENCH_ITERATIONS=20 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:bench
```

At least one warmup and two measurement iterations are enforced.

## What runs

Gradle builds one Java 8 benchmark JAR and the transpiler fat JAR. The Python
driver then:

1. runs the unmodified benchmark JAR on the JVM;
2. transpiles only `benchmarks/kernels/**` from that same JAR with
   `--codegen=ir`, then configures, compiles, and runs its shared library;
3. classifies each measured IR method from generated `// IR codegen:` markers
   or restore-original logs;
4. rejects unclassified methods, mismatched kernel sets, or checksums.

Transpiler stdout/stderr is retained in
`build/benchmarks/logs/transpile-ir.log`. Generated trees remain under
`build/benchmarks/work/ir/transpiled`.

Every measured sample uses `System.nanoTime()`. An untimed checksum preflight
and warmup iterations precede the recorded samples. A volatile sink consumes
each result. The report retains every sample and computes mean and median from
only those recorded samples.

The kernels are:

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
task fail. No native timings are emitted unless the transformed JAR actually
runs successfully. A restored-bytecode method is reported as restore and is
not labeled as an IR timing.

## Interpreting results

This is a small custom runner rather than JMH so the project retains its Java 8
main compilation and adds no benchmark dependency. It provides warmup, repeated
samples, raw values, median, and mean, but not process forks or confidence
intervals. Treat a single run as local diagnostic evidence, not a release
threshold or a general performance claim. Compare repeated runs on controlled
hardware before drawing conclusions.

The latest three-mode run, recorded after IR phase 19 admission landed, is in
[`results-ir-vs-legacy-phase19.md`](results-ir-vs-legacy-phase19.md). The
pre-phase-19 record remains in
[`results-ir-vs-legacy-master.md`](results-ir-vs-legacy-master.md).
