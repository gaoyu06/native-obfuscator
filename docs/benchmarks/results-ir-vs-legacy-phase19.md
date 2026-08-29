# Post-phase-19 JVM versus legacy/IR JNI benchmark

Recorded on 2026-08-29 in one Cursor Cloud VM from `origin/master`
`76ebeddb005e01033523384275c8c0c1641ada81`, the first master tip that includes
IR phase 19 (`LAND`/`LOR`/`LXOR` and `LSHL`/`LSHR`/`LUSHR` admission). The
three modes used the same benchmark JAR, kernels, checksum preflight, five
warmups, and ten measured iterations. Times are nanoseconds per exact sample
workload.

The plain JVM, legacy JNI, and IR-mode JNI processes all completed. Both
native pipelines completed transpilation, CMake configuration, GCC/g++
compilation, linking, JNI execution, and cross-mode checksum validation.

This run supersedes
[`results-ir-vs-legacy-master.md`](results-ir-vs-legacy-master.md) (recorded on
pre-phase-19 `e997d71c7525a4c607e29b6eb1ae9140a72dfd22`), where the
`integer-loop` IR-mode row was a legacy fallback (opcode 125 `LUSHR`) and
`recursion` was mixed (`recurse` fell back on opcode 131 `LXOR`). Those old
numbers remain valid only as the pre-phase-19 record; the timings below were
measured fresh on this tree, not copied.

## Commands

The top-level commands executed sequentially from `/workspace` were:

```sh
python3 -m py_compile benchmarks/run.py
BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ ./gradlew :obfuscator:bench
```

The task recorded these subprocess commands:

```sh
java -jar /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar --mode=plain-jvm --warmup=5 --iterations=10
java -jar /workspace/obfuscator/build/libs/obfuscator.jar --white-list=/workspace/benchmarks/whitelist.txt --plain-lib-name=native_library --codegen=legacy /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar /workspace/build/benchmarks/work/legacy/transpiled
cmake -S /workspace/build/benchmarks/work/legacy/transpiled/cpp -B /workspace/build/benchmarks/work/legacy/transpiled/cpp/cmake-build -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=/usr/bin/x86_64-linux-gnu-gcc-13 -DCMAKE_CXX_COMPILER=/usr/bin/x86_64-linux-gnu-g++-13
cmake --build /workspace/build/benchmarks/work/legacy/transpiled/cpp/cmake-build --config Release --parallel
java -Djava.library.path=/workspace/build/benchmarks/work/legacy/transpiled -jar /workspace/build/benchmarks/work/legacy/transpiled/transpiler-benchmarks.jar --mode=transpiled-jni-legacy --warmup=5 --iterations=10
java -jar /workspace/obfuscator/build/libs/obfuscator.jar --white-list=/workspace/benchmarks/whitelist.txt --plain-lib-name=native_library --codegen=ir /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar /workspace/build/benchmarks/work/ir/transpiled
cmake -S /workspace/build/benchmarks/work/ir/transpiled/cpp -B /workspace/build/benchmarks/work/ir/transpiled/cpp/cmake-build -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=/usr/bin/x86_64-linux-gnu-gcc-13 -DCMAKE_CXX_COMPILER=/usr/bin/x86_64-linux-gnu-g++-13
cmake --build /workspace/build/benchmarks/work/ir/transpiled/cpp/cmake-build --config Release --parallel
java -Djava.library.path=/workspace/build/benchmarks/work/ir/transpiled -jar /workspace/build/benchmarks/work/ir/transpiled/transpiler-benchmarks.jar --mode=transpiled-jni-ir --warmup=5 --iterations=10
```

## Exact environment

`uname -a`:

```text
Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
```

`java -version`:

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
```

`cmake --version`:

```text
cmake version 3.28.3

CMake suite maintained and supported by Kitware (kitware.com/cmake).
```

`/usr/bin/x86_64-linux-gnu-gcc-13 --version` (the C compiler CMake used):

```text
x86_64-linux-gnu-gcc-13 (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Copyright (C) 2023 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```

`/usr/bin/x86_64-linux-gnu-g++-13 --version` (the C++ compiler CMake used):

```text
x86_64-linux-gnu-g++-13 (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Copyright (C) 2023 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```

## Summary

| Kernel | JVM median | JVM mean | Legacy JNI median | Legacy JNI mean | IR-mode JNI median | IR-mode JNI mean | Actual IR path |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `integer-loop` | 10,022,141.0 | 10,042,374.0 | 182,379,510.0 | 182,354,948.7 | 10,016,799.0 | 10,024,490.0 | **Stayed on IR** |
| `string-concat-hash` | 1,159,325.5 | 1,254,919.0 | 11,850,265.5 | 11,540,741.6 | 7,919,348.5 | 7,697,399.5 | **Stayed on IR** |
| `recursion` | 65,068.0 | 64,754.9 | 15,837,322.5 | 15,838,187.3 | 11,240,118.5 | 11,239,278.4 | **Stayed on IR** |

## IR path audit

Every measured method emitted an `// IR codegen:` marker in the generated
sources under `build/benchmarks/work/ir/transpiled/cpp/output/`, and
`build/benchmarks/logs/transpile-ir.log` contains zero
`IR codegen unsupported` / `falling back to legacy` lines. The two methods
that fell back in the pre-phase-19 run now admit.

| Kernel | Measured method | Path | Evidence |
| --- | --- | --- | --- |
| `integer-loop` | `benchmarks/kernels/IntegerLoopKernel.run(I)J` | IR | Generated `// IR codegen: benchmarks/kernels/IntegerLoopKernel.run(I)J` (`benchmarks_kernels_IntegerLoopKernel_0.cpp`); fell back on opcode 125 `LUSHR` before phase 19 |
| `string-concat-hash` | `benchmarks/kernels/StringConcatHashKernel.run(I)I` | IR | Generated `// IR codegen: benchmarks/kernels/StringConcatHashKernel.run(I)I` (`benchmarks_kernels_StringConcatHashKernel_2.cpp`) |
| `recursion` | `benchmarks/kernels/RecursionKernel.run(II)J` | IR | Generated `// IR codegen: benchmarks/kernels/RecursionKernel.run(II)J` (`benchmarks_kernels_RecursionKernel_1.cpp`) |
| `recursion` | `benchmarks/kernels/RecursionKernel.recurse(IJ)J` | IR | Generated `// IR codegen: benchmarks/kernels/RecursionKernel.recurse(IJ)J` (`benchmarks_kernels_RecursionKernel_1.cpp`); fell back on opcode 131 `LXOR` before phase 19 |

Therefore all three kernels are pure direct-IR measurements in this run: for
the first time on master, the `integer-loop` and `recursion` IR-mode columns
are IR timings rather than fallback or mixed data.

## Raw samples and checksums

Every checksum below matched across all three modes.

### Plain JVM

| Kernel | Checksum | Samples (ns) | Median | Mean |
| --- | ---: | --- | ---: | ---: |
| `integer-loop` | -5,663,617,524,014,644,874 | 10,040,742; 10,021,295; 10,088,441; 10,019,456; 10,022,987; 10,122,365; 10,079,160; 10,008,478; 10,011,839; 10,008,977 | 10,022,141.0 | 10,042,374.0 |
| `string-concat-hash` | -124,029,673,400 | 610,718; 593,366; 805,689; 1,609,986; 2,565,413; 1,174,431; 1,038,744; 1,455,012; 1,144,220; 1,551,611 | 1,159,325.5 | 1,254,919.0 |
| `recursion` | 3,055,512 | 64,368; 63,601; 63,574; 63,575; 65,068; 65,077; 65,068; 65,076; 67,067; 65,075 | 65,068.0 | 64,754.9 |

### Legacy JNI

| Kernel | Checksum | Samples (ns) | Median | Mean |
| --- | ---: | --- | ---: | ---: |
| `integer-loop` | -5,663,617,524,014,644,874 | 182,025,020; 182,309,451; 182,567,252; 182,449,569; 182,239,291; 182,151,076; 182,648,898; 182,502,560; 182,196,894; 182,459,476 | 182,379,510.0 | 182,354,948.7 |
| `string-concat-hash` | -124,029,673,400 | 10,684,713; 10,595,367; 10,937,860; 11,948,302; 11,865,876; 11,895,208; 11,945,493; 11,852,517; 11,848,014; 11,834,066 | 11,850,265.5 | 11,540,741.6 |
| `recursion` | 3,055,512 | 15,820,427; 15,766,360; 15,854,218; 15,791,056; 15,885,503; 15,721,921; 15,688,697; 15,915,213; 16,003,586; 15,934,892 | 15,837,322.5 | 15,838,187.3 |

### IR-mode JNI

All three kernels stayed fully on IR (see the audit table above), so every row
below is a direct-IR measurement.

| Kernel | Path | Checksum | Samples (ns) | Median | Mean |
| --- | --- | ---: | --- | ---: | ---: |
| `integer-loop` | IR | -5,663,617,524,014,644,874 | 10,032,970; 10,011,898; 10,017,368; 10,016,230; 10,029,899; 10,077,193; 10,012,010; 10,009,196; 10,012,343; 10,025,793 | 10,016,799.0 | 10,024,490.0 |
| `string-concat-hash` | IR | -124,029,673,400 | 7,009,690; 7,001,704; 7,172,432; 7,920,999; 7,994,889; 8,129,430; 7,940,690; 7,912,667; 7,917,698; 7,973,796 | 7,919,348.5 | 7,697,399.5 |
| `recursion` | IR | 3,055,512 | 11,264,382; 11,218,293; 11,264,433; 11,343,971; 11,261,944; 11,348,775; 11,156,504; 11,164,174; 11,202,645; 11,167,663 | 11,240,118.5 | 11,239,278.4 |

## Must not be read as

This is a local, single-process diagnostic only. It must not be read as a
portable speedup, a claim that native code generally beats HotSpot, a
HotSpot-beating claim for these workloads, a release gate, or a JDK support
badge. The `integer-loop` IR median landing near the JVM median is a
single-machine observation on this exact kernel and compiler; it does not
generalize. This run does not add an evaluator, does not back-fill the #53
eval result (which remains `N/A` in
[`results-ir-eval-lower.md`](results-ir-eval-lower.md)), does not change the
`legacy` default, and does not complete the production goal. Requirement 7
(resisting unaided Sol-class recovery) remains unmet.
