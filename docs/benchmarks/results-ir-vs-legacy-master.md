# Current-master JVM versus legacy/IR JNI benchmark

Recorded on 2026-08-29 in one Cursor Cloud VM from `origin/master`
`e997d71c7525a4c607e29b6eb1ae9140a72dfd22`. The three modes used the same
benchmark JAR, kernels, checksum preflight, five warmups, and ten measured
iterations. Times are nanoseconds per exact sample workload.

The plain JVM, legacy JNI, and IR-mode JNI processes all completed. Both native
pipelines completed transpilation, CMake configuration, GCC/g++ compilation,
linking, JNI execution, and cross-mode checksum validation.

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

`/usr/bin/x86_64-linux-gnu-gcc-13 --version`:

```text
x86_64-linux-gnu-gcc-13 (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Copyright (C) 2023 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```

`/usr/bin/x86_64-linux-gnu-g++-13 --version`:

```text
x86_64-linux-gnu-g++-13 (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Copyright (C) 2023 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```

## Summary

| Kernel | JVM median | JVM mean | Legacy JNI median | Legacy JNI mean | IR-mode JNI median | IR-mode JNI mean | Actual IR path |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `integer-loop` | 10,479,264.5 | 10,724,077.9 | 182,843,205.5 | 187,845,507.1 | 183,656,531.5 | 184,234,070.5 | **Fallback; not an IR timing** |
| `string-concat-hash` | 1,139,795.5 | 1,229,666.8 | 11,885,163.0 | 11,571,004.0 | 7,938,482.5 | 7,679,938.6 | **Stayed on IR** |
| `recursion` | 63,561.5 | 63,574.1 | 16,026,360.0 | 16,061,597.6 | 15,866,969.5 | 15,835,764.7 | **Mixed IR/fallback; not a pure IR timing** |

## IR path audit

| Kernel | Measured method | Path | Evidence |
| --- | --- | --- | --- |
| `integer-loop` | `benchmarks/kernels/IntegerLoopKernel.run(I)J` | Legacy fallback | IR rejected bytecode instruction 22, opcode 125 |
| `string-concat-hash` | `benchmarks/kernels/StringConcatHashKernel.run(I)I` | IR | Generated `// IR codegen: benchmarks/kernels/StringConcatHashKernel.run(I)I` |
| `recursion` | `benchmarks/kernels/RecursionKernel.run(II)J` | IR | Generated `// IR codegen: benchmarks/kernels/RecursionKernel.run(II)J` |
| `recursion` | `benchmarks/kernels/RecursionKernel.recurse(IJ)J` | Legacy fallback | IR rejected bytecode instruction 21, opcode 131 |

The exact fallback records were:

```text
03:59:09 INFO  NativeObfuscator:344  | IR codegen unsupported for benchmarks/kernels/IntegerLoopKernel#run(I)J: Unsupported instruction for phase-two IR at bytecode instruction 22 (opcode 125); falling back to legacy for this method
03:59:09 INFO  NativeObfuscator:344  | IR codegen unsupported for benchmarks/kernels/RecursionKernel#recurse(IJ)J: Unsupported instruction for phase-two IR at bytecode instruction 21 (opcode 131); falling back to legacy for this method
```

Therefore, only `string-concat-hash` is a fully direct-IR kernel measurement in
this run. The `integer-loop` IR-mode samples measure legacy fallback.
`recursion` combines an IR-generated entry method with a legacy-fallback
recursive helper and must not be presented as a pure IR result.

## Raw samples and checksums

Every checksum below matched across all three modes.

### Plain JVM

| Kernel | Checksum | Samples (ns) | Median | Mean |
| --- | ---: | --- | ---: | ---: |
| `integer-loop` | -5,663,617,524,014,644,874 | 10,138,621; 10,204,004; 11,131,429; 11,245,624; 12,012,829; 10,158,584; 10,159,564; 11,335,617; 10,099,982; 10,754,525 | 10,479,264.5 | 10,724,077.9 |
| `string-concat-hash` | -124,029,673,400 | 635,111; 531,366; 1,115,226; 2,745,866; 1,282,461; 1,412,314; 1,162,516; 1,218,810; 1,117,075; 1,075,923 | 1,139,795.5 | 1,229,666.8 |
| `recursion` | 3,055,512 | 63,698; 63,604; 63,508; 63,573; 63,563; 63,558; 63,567; 63,558; 63,552; 63,560 | 63,561.5 | 63,574.1 |

### Legacy JNI

| Kernel | Checksum | Samples (ns) | Median | Mean |
| --- | ---: | --- | ---: | ---: |
| `integer-loop` | -5,663,617,524,014,644,874 | 182,466,579; 182,831,806; 182,698,094; 182,729,652; 182,854,605; 182,765,900; 190,948,607; 196,075,843; 194,464,752; 200,619,233 | 182,843,205.5 | 187,845,507.1 |
| `string-concat-hash` | -124,029,673,400 | 10,754,278; 10,619,571; 10,849,418; 11,947,507; 11,903,418; 11,866,908; 11,938,942; 11,802,727; 12,103,983; 11,923,288 | 11,885,163.0 | 11,571,004.0 |
| `recursion` | 3,055,512 | 16,085,600; 15,947,559; 15,963,480; 16,397,460; 15,950,006; 16,192,038; 16,077,270; 15,949,843; 16,047,041; 16,005,679 | 16,026,360.0 | 16,061,597.6 |

### IR-mode JNI

Only `string-concat-hash` stayed fully on IR. The path column prevents the
fallback and mixed rows from being mistaken for pure IR measurements.

| Kernel | Path | Checksum | Samples (ns) | Median | Mean |
| --- | --- | ---: | --- | ---: | ---: |
| `integer-loop` | Legacy fallback | -5,663,617,524,014,644,874 | 190,335,415; 184,009,069; 184,120,230; 183,551,764; 183,761,299; 183,065,999; 183,054,839; 182,982,960; 184,107,898; 183,351,232 | 183,656,531.5 | 184,234,070.5 |
| `string-concat-hash` | IR | -124,029,673,400 | 6,943,661; 6,900,209; 7,161,841; 7,956,779; 8,034,979; 7,975,869; 8,029,097; 7,919,986; 7,933,276; 7,943,689 | 7,938,482.5 | 7,679,938.6 |
| `recursion` | IR entry + legacy-fallback helper | 3,055,512 | 15,874,683; 15,821,235; 15,780,885; 15,884,488; 15,900,334; 15,746,160; 15,666,662; 15,859,256; 15,898,152; 15,925,792 | 15,866,969.5 | 15,835,764.7 |

## Must not be read as

This is a local, single-process diagnostic only. It must not be read as a
portable speedup, a claim that native code generally beats HotSpot, a
HotSpot-beating claim for this workload, or a release gate. The fallback rows
are not IR performance data. This run does not add an evaluator, does not
back-fill the #53 eval result (which remains `N/A`), and does not complete the
production goal.
