# Local IR evaluator-lowering benchmark result

Recorded on 2026-08-28 in one Cursor Cloud VM. This is local diagnostic
compiler/benchmark evidence, not a portable performance result.

The requested evaluator comparison could not produce a valid evaluator timing:
`IrFriendlyIntKernel.run(I)I` contains `USHR`, which the current evaluator
lowering rejects. The `--ir-lower=eval` run therefore used the logged
per-method legacy fallback. Its observed samples are retained below, but they
are explicitly excluded from evaluator performance.

## Integrated revisions

This branch started at benchmark tip `7221987` from
`cursor/bench-ir-vs-legacy-6d81`. The evaluator is not a descendant of that
branch: both histories meet at `ebc6ffc`. Evaluator review tip `52f5efb` from
`cursor/ir-evaluator-review-6d81` was merged with `--no-ff` as `bd0793f`;
the only merge conflict was the independently added `PR_BODY.md`. Benchmark
mode/path support was then added in `ca1f7f7`.

The benchmark side contributes these commits after the common ancestor:

```text
0f55ba7 Add reproducible JVM and JNI benchmark harness
48eda05 Fix benchmark task artifact wiring
4d39ebe Select an available host C++ compiler
c79f3b8 Document benchmark usage and local results
5bb1fa4 Extend benchmark harness for legacy and IR codegen
a9497cd Resolve compiler names through PATH in benchmark driver
7221987 Record local IR versus legacy benchmark results
```

The evaluator side contributes phase-4/compiler/evaluator commits
`f44da88`, `2a56d83`, `531142d`, `d151041`, `ab94362`, `564589c`,
`dbba07b`, `75cca92`, `0588cf2`, `223aecd`, and review commit `52f5efb`.

## Command and environment

All three native modes ran in one Gradle invocation with identical counts and
the required GCC selection:

```sh
BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:bench :obfuscator:benchIrDirect \
  :obfuscator:benchIrEval
```

Each task ran an untimed checksum preflight, 5 warmups, then 10 recorded
samples of the same workload: 5,000,000 iterations in
`IrFriendlyIntKernel.run(I)I`. Each native task also ran a fresh plain-JVM
reference with the same counts. Times are `System.nanoTime()` nanoseconds per
complete sample workload.

| Item | Recorded value |
| --- | --- |
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04, 64-bit Server VM |
| C compiler | `/usr/bin/x86_64-linux-gnu-gcc-13`, GCC 13.3.0 |
| C++ compiler | `/usr/bin/x86_64-linux-gnu-g++-13`, g++ 13.3.0 |
| CMake | 3.28.3 |
| CPU model | Intel(R) Xeon(R) Processor |
| OS | Linux 6.12.94+, x86_64, glibc 2.39 |
| Timer | `System.nanoTime()` |
| Warmup / measured iterations | 5 / 10 for every run |
| Checksum | 2,038,221,507 for every JVM/native run |
| Legacy pipeline | PASS: transpile, CMake, g++, JNI, checksum |
| Direct-IR pipeline | PASS: transpile, CMake, g++, JNI, checksum |
| Eval-selected pipeline | PASS, but measured method used legacy fallback |

## Summary

The primary JVM row is the reference immediately preceding the legacy native
run. The two additional JVM references are retained in the raw table.

| Reported mode | Actual measured path | Median (ns) | Mean (ns) | Valid for requested mode? |
| --- | --- | ---: | ---: | --- |
| JVM | plain JVM | 12,207,144.5 | 12,251,776.8 | Yes |
| legacy | legacy JNI | 202,090,247.0 | 200,472,608.4 | Yes |
| `--ir-lower=direct` | direct IR JNI | 11,311,481.5 | 11,271,388.4 | Yes |
| `--ir-lower=eval` | legacy fallback | **N/A** | **N/A** | No |
| Eval-selected fallback observation | legacy JNI fallback | 193,719,578.5 | 192,439,023.1 | No; not an eval number |

The recorded medians establish only these local values. No evaluator speedup
or slowdown can be calculated because evaluator lowering did not accept the
kernel. No portability claim is made.

## Actual-path and fallback table

| Requested mode | Measured method | Actual path | Fallback? | Authoritative evidence |
| --- | --- | --- | --- | --- |
| JVM | `benchmarks.kernels.IrFriendlyIntKernel.run(I)I` | JVM bytecode/JIT | No | Unmodified benchmark JAR, `--mode=plain-jvm` |
| `--codegen=legacy` | `benchmarks/kernels/IrFriendlyIntKernel.run(I)I` | legacy JNI | No | Explicit `--codegen=legacy` |
| `--codegen=ir --ir-lower=direct` | same | direct IR JNI | No | Generated `// IR codegen: benchmarks/kernels/IrFriendlyIntKernel.run(I)I` |
| `--codegen=ir --ir-lower=eval` | same | legacy JNI fallback | **Yes** | Log: `Unsupported evaluator binary operation USHR at bytecode instruction 22; falling back to legacy for this method` |

The direct generated source was
`build/benchmarks/work/ir-direct/transpiled/cpp/output/benchmarks_kernels_IrFriendlyIntKernel_1.cpp`.
The full eval fallback record was:

```text
IR codegen unsupported for benchmarks/kernels/IrFriendlyIntKernel#run(I)I:
Unsupported evaluator binary operation USHR at bytecode instruction 22;
falling back to legacy for this method
```

## Every recorded sample

All rows have checksum `2,038,221,507` and passed checksum comparison.

| Run | Samples (ns) | Median (ns) | Mean (ns) | Interpretation |
| --- | --- | ---: | ---: | --- |
| JVM before legacy | 12,903,414; 12,504,756; 12,409,659; 12,473,752; 12,136,802; 12,237,301; 11,930,751; 11,615,927; 12,128,418; 12,176,988 | 12,207,144.5 | 12,251,776.8 | Primary JVM reference |
| Legacy native | 202,193,333; 200,230,030; 205,045,872; 203,924,317; 206,335,447; 204,300,938; 201,987,161; 200,503,011; 191,869,849; 188,336,126 | 202,090,247.0 | 200,472,608.4 | Valid legacy timing |
| JVM before direct IR | 11,535,585; 11,473,321; 11,488,276; 11,082,482; 11,027,652; 11,273,962; 11,684,809; 11,730,694; 11,607,855; 11,369,840 | 11,480,798.5 | 11,427,447.6 | JVM repeat |
| Direct IR native | 11,301,529; 11,344,716; 11,321,434; 11,449,685; 11,154,809; 11,393,673; 11,389,237; 11,288,668; 11,075,672; 10,994,461 | 11,311,481.5 | 11,271,388.4 | Valid direct-IR timing |
| JVM before eval selection | 11,005,838; 10,812,691; 10,900,911; 11,069,462; 10,746,441; 10,856,288; 11,088,975; 11,152,831; 11,020,280; 11,236,830 | 11,013,059.0 | 10,989,054.7 | JVM repeat |
| Eval-selected native | 182,890,264; 191,297,580; 193,181,259; 187,015,786; 184,204,126; 194,257,898; 198,389,687; 197,233,573; 198,494,300; 197,425,758 | 193,719,578.5 | 192,439,023.1 | Excluded: legacy fallback, not evaluator |

Machine-readable reports were generated as
`build/benchmarks/results-{legacy,ir-direct,ir-eval}.json`; transpiler logs and
generated C++ remained under ignored `build/benchmarks/` paths.

## Verification

Before the timed run:

```sh
python3 -m py_compile benchmarks/run.py
./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest
```

Both commands passed. The Gradle run executed 27 focused tests with no reported
failure or skip, including the g++ direct/evaluator checks. The timed Gradle
invocation finished `BUILD SUCCESSFUL`; all three reports had top-level
`status: PASS`, all CMake/g++/JNI stages passed, and all checksums matched.
