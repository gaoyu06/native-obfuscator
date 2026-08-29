# Local IR evaluator benchmark after `IUSHR`

Recorded on 2026-08-28 in one Cursor Cloud VM. This is local diagnostic
compiler/benchmark evidence, not a portable performance result.

`IrFriendlyIntKernel.run(I)I` stayed on the evaluator path. The generated
source contains its evaluator-data marker, and its transpile log contains no
fallback for that method (including no `IUSHR` fallback). The same log does
contain expected fallback records for other, unmeasured methods; those do not
change the measured kernel's classified path.

## Revisions and command

The evaluator base was `21f474d` from `cursor/ir-evaluator-ushr-6d81`.
The benchmark driver was integrated from
`cursor/bench-ir-eval-lower-6d81` through its harness commit `ca1f7f7`.

All modes measured the same workload: 5,000,000 loop iterations in
`IrFriendlyIntKernel.run(I)I`. Each process performed an untimed checksum
preflight, 5 warmups, and 10 recorded samples. The native modes used identical
counts and the required GCC selection:

```sh
BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:benchIrEval
BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ \
  ./gradlew :obfuscator:bench :obfuscator:benchIrDirect
```

Times are `System.nanoTime()` nanoseconds per complete sample workload.

## Environment and correctness

| Item | Recorded value |
| --- | --- |
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04, 64-bit Server VM |
| C compiler | `/usr/bin/x86_64-linux-gnu-gcc-13`, GCC 13.3.0 |
| C++ compiler | `/usr/bin/x86_64-linux-gnu-g++-13`, g++ 13.3.0 |
| CMake | 3.28.3 |
| CPU model | Intel(R) Xeon(R) Processor |
| OS | Linux 6.12.94+, x86_64, glibc 2.39 |
| Timer | `System.nanoTime()` |
| Warmup / measured iterations | 5 / 10 in every process |
| Checksum | 2,038,221,507 in every JVM and native run |
| Pipelines | Transpile, CMake, g++, JNI, and checksum all passed |

## Summary

The primary JVM row is the JVM reference paired with the legacy run. The two
other JVM references are retained in the raw table below rather than combined
across separate JVM processes.

| Mode | Actual measured path | Median (ns) | Mean (ns) |
| --- | --- | ---: | ---: |
| JVM | plain JVM bytecode/JIT | 10,017,146.0 | 10,017,598.3 |
| legacy | legacy JNI | 167,870,311.5 | 168,760,115.9 |
| `--ir-lower=direct` | direct IR JNI | 10,021,957.0 | 10,028,611.5 |
| `--ir-lower=eval` | evaluator IR JNI | 411,875,537.5 | 412,122,294.3 |

These are only the recorded local values. No portability or speedup claim is
made.

## Actual-path and fallback table

| Requested mode | Measured method | Actual path | Fallback? | Authoritative evidence |
| --- | --- | --- | --- | --- |
| JVM | `benchmarks.kernels.IrFriendlyIntKernel.run(I)I` | JVM bytecode/JIT | No | Unmodified benchmark JAR with `--mode=plain-jvm` |
| `--codegen=legacy` | `benchmarks/kernels/IrFriendlyIntKernel.run(I)I` | legacy JNI | No | Explicit `--codegen=legacy`; harness classification `legacy` |
| `--codegen=ir --ir-lower=direct` | same | direct IR JNI | No | Generated `// IR codegen: benchmarks/kernels/IrFriendlyIntKernel.run(I)I`; harness classification `ir-direct` |
| `--codegen=ir --ir-lower=eval` | same | evaluator IR JNI | **No** | Generated `// IR evaluator data: benchmarks/kernels/IrFriendlyIntKernel.run(I)I`; harness classification `ir-eval`; no target-method fallback in `transpile-ir-eval.log` |

The evaluator marker is in
`build/benchmarks/work/ir-eval/transpiled/cpp/output/benchmarks_kernels_IrFriendlyIntKernel_1.cpp`.
The eval transpile log processes the target method without an unsupported
record between its `Preprocessing`/`Processing` entries and the following
class; specifically, there is no `Unsupported evaluator binary operation USHR`
record. Machine-readable reports and full logs were generated under ignored
`build/benchmarks/`.

## Every recorded sample

All rows have checksum `2,038,221,507`.

| Run | Samples (ns) | Median (ns) | Mean (ns) |
| --- | --- | ---: | ---: |
| JVM paired with eval | 10,092,512; 10,250,898; 10,048,086; 10,088,045; 10,083,763; 10,087,555; 10,108,488; 10,073,064; 10,090,978; 10,027,693 | 10,087,800.0 | 10,095,108.2 |
| Evaluator IR native | 412,888,402; 412,097,880; 411,653,195; 412,532,306; 413,393,247; 411,470,244; 410,686,097; 410,468,410; 410,598,886; 415,434,276 | 411,875,537.5 | 412,122,294.3 |
| JVM paired with legacy (primary) | 10,018,043; 10,013,822; 10,020,830; 10,012,339; 10,031,271; 10,018,343; 10,016,249; 10,011,982; 10,020,767; 10,012,337 | 10,017,146.0 | 10,017,598.3 |
| Legacy native | 172,543,676; 167,639,617; 167,133,389; 168,655,118; 167,869,227; 170,295,998; 167,657,783; 167,871,396; 170,477,417; 167,457,538 | 167,870,311.5 | 168,760,115.9 |
| JVM paired with direct IR | 10,015,004; 15,012,166; 10,012,547; 10,095,723; 10,123,386; 10,123,016; 10,015,815; 10,108,191; 10,081,492; 10,093,874 | 10,094,798.5 | 10,568,121.4 |
| Direct IR native | 10,043,366; 10,025,246; 10,025,653; 10,016,493; 10,011,468; 10,018,668; 10,087,471; 10,028,691; 10,013,625; 10,015,434 | 10,021,957.0 | 10,028,611.5 |

## Verification

Before measuring, these checks passed:

```sh
python3 -m py_compile benchmarks/run.py
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest
```

The focused Gradle selection executed 28 tests with no failure or skip,
including generated-source and linked evaluator coverage. All three benchmark
reports have top-level `status: PASS`, their recorded method-path
classifications match the table above, and their JVM/native checksums match.
