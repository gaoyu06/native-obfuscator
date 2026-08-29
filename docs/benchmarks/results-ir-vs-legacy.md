# Local IR versus legacy benchmark result

Recorded on 2026-08-28 in one Cursor Cloud VM. Both native runs completed
transpilation, CMake configuration, GCC compilation, JNI execution, and
checksum validation. Times are nanoseconds per exact sample workload.

These are local diagnostic numbers, not portable performance claims. The
current IR subset fell back to legacy for three of the four kernels; only
`ir-friendly-int-loop` is a valid IR-versus-legacy comparison.

## Commands and environment

```sh
BENCH_WARMUP=3 BENCH_ITERATIONS=5 CC=gcc CXX=g++ ./gradlew :obfuscator:bench
BENCH_WARMUP=3 BENCH_ITERATIONS=5 CC=gcc CXX=g++ ./gradlew :obfuscator:benchIr
```

| Item | Recorded value |
| --- | --- |
| Warmup / measured iterations | 3 / 5 |
| Timer | `System.nanoTime()` |
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04, 64-bit Server VM |
| C compiler | `/usr/bin/x86_64-linux-gnu-gcc-13`, GCC 13.3.0 |
| C++ compiler | `/usr/bin/x86_64-linux-gnu-g++-13`, g++ 13.3.0 |
| CMake | 3.28.3 |
| CPU model | Intel(R) Xeon(R) Processor |
| OS | Linux 6.12.94+, x86_64, glibc 2.39 |
| Legacy native pipeline | PASS |
| IR native pipeline | PASS |

The driver captured complete transpiler stdout/stderr in
`build/benchmarks/logs/transpile-legacy.log` and
`build/benchmarks/logs/transpile-ir.log`. The generated native sources were
also retained under `build/benchmarks/work/{legacy,ir}/transpiled/cpp`.

## Summary

The plain-JVM columns use the reference run immediately preceding the legacy
native run. The IR task's second plain-JVM reference is recorded separately
below.

| Kernel | Plain JVM median | Plain JVM mean | Legacy median | Legacy mean | IR-task native median | IR-task native mean | IR timing valid? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| ir-friendly-int-loop | 10,092,919 | 10,080,336.0 | 182,135,075 | 182,067,279.2 | 10,023,918 | 10,042,392.4 | Yes |
| integer-loop | 10,069,516 | 10,074,657.8 | 182,120,623 | 182,303,066.0 | 182,374,183 | 182,969,423.6 | No: fallback |
| string-concat-hash | 660,985 | 927,396.8 | 11,406,923 | 11,983,095.4 | 11,483,706 | 12,331,338.0 | No: fallback |
| recursion | 82,496 | 82,461.2 | 14,354,422 | 14,377,993.2 | 12,565,568 | 12,543,278.6 | No: both methods fallback |

For the one valid IR kernel, the recorded median is 18.170x lower than the
legacy median. It is 0.684% lower than the primary plain-JVM median. The large
local IR-versus-legacy difference is supported by these medians; the small
IR-versus-JVM difference should not be generalized from one five-sample,
single-process run.

The three fallback rows are useful repeat measurements of the legacy emitter,
but they are not IR performance results. In particular,
`string-concat-hash` and `recursion` must not be used for an IR speedup claim.

## Method code-generation paths

| Kernel | Measured method | Legacy task | IR task | Evidence / reason |
| --- | --- | --- | --- | --- |
| ir-friendly-int-loop | `benchmarks/kernels/IrFriendlyIntKernel.run(I)I` | legacy | **IR** | Generated `// IR codegen: benchmarks/kernels/IrFriendlyIntKernel.run(I)I` |
| integer-loop | `benchmarks/kernels/IntegerLoopKernel.run(I)J` | legacy | legacy fallback | Log: only void and JVM int-carrier returns supported |
| string-concat-hash | `benchmarks/kernels/StringConcatHashKernel.run(I)I` | legacy | legacy fallback | Log: unsupported bytecode instruction 2, opcode 18 |
| recursion | `benchmarks/kernels/RecursionKernel.run(II)J` | legacy | legacy fallback | Log: only void and JVM int-carrier returns supported |
| recursion | `benchmarks/kernels/RecursionKernel.recurse(IJ)J` | legacy | legacy fallback | Log: only void and JVM int-carrier returns supported |

Relevant IR transpiler log records:

```text
IR codegen unsupported for benchmarks/kernels/IntegerLoopKernel#run(I)J:
Only void and JVM int-carrier method returns are supported; falling back to legacy
IR codegen unsupported for benchmarks/kernels/RecursionKernel#run(II)J:
Only void and JVM int-carrier method returns are supported; falling back to legacy
IR codegen unsupported for benchmarks/kernels/RecursionKernel#recurse(IJ)J:
Only void and JVM int-carrier method returns are supported; falling back to legacy
IR codegen unsupported for benchmarks/kernels/StringConcatHashKernel#run(I)I:
Unsupported instruction at bytecode instruction 2 (opcode 18); falling back to legacy
```

`IrFriendlyIntKernel.run(I)I` had no fallback record, and its generated C++
body contains the IR marker quoted in the table.

## Raw samples

### Plain JVM (primary reference from legacy task)

| Kernel | Checksum | Samples (ns) | Median | Mean | Check |
| --- | ---: | --- | ---: | ---: | --- |
| ir-friendly-int-loop | 2,038,221,507 | 10,099,402; 10,085,454; 10,100,023; 10,092,919; 10,023,882 | 10,092,919 | 10,080,336.0 | PASS |
| integer-loop | -5,663,617,524,014,644,874 | 10,022,361; 10,129,624; 10,069,441; 10,069,516; 10,082,347 | 10,069,516 | 10,074,657.8 | PASS |
| string-concat-hash | -124,029,673,400 | 1,906,490; 616,380; 660,985; 642,046; 811,083 | 660,985 | 927,396.8 | PASS |
| recursion | 3,055,512 | 82,408; 82,673; 82,110; 82,619; 82,496 | 82,496 | 82,461.2 | PASS |

### Legacy native

| Kernel | Checksum | Samples (ns) | Median | Mean | Check |
| --- | ---: | --- | ---: | ---: | --- |
| ir-friendly-int-loop | 2,038,221,507 | 182,135,075; 182,164,281; 181,904,439; 182,267,759; 181,864,842 | 182,135,075 | 182,067,279.2 | PASS |
| integer-loop | -5,663,617,524,014,644,874 | 182,113,197; 182,700,706; 182,584,561; 182,120,623; 181,996,243 | 182,120,623 | 182,303,066.0 | PASS |
| string-concat-hash | -124,029,673,400 | 14,049,135; 11,382,215; 11,406,923; 11,350,781; 11,726,423 | 11,406,923 | 11,983,095.4 | PASS |
| recursion | 3,055,512 | 12,782,208; 12,702,366; 14,354,422; 16,042,775; 16,008,195 | 14,354,422 | 14,377,993.2 | PASS |

### IR-task native

Only the first row remained on the IR path.

| Kernel | Checksum | Samples (ns) | Median | Mean | Check |
| --- | ---: | --- | ---: | ---: | --- |
| ir-friendly-int-loop | 2,038,221,507 | 10,023,918; 10,073,668; 10,007,893; 10,017,732; 10,088,751 | 10,023,918 | 10,042,392.4 | PASS |
| integer-loop (fallback) | -5,663,617,524,014,644,874 | 182,742,650; 182,013,021; 182,364,655; 182,374,183; 185,352,609 | 182,374,183 | 182,969,423.6 | PASS |
| string-concat-hash (fallback) | -124,029,673,400 | 15,905,167; 11,483,706; 11,386,695; 11,304,092; 11,577,030 | 11,483,706 | 12,331,338.0 | PASS |
| recursion (fallback) | 3,055,512 | 12,565,568; 12,630,744; 12,618,150; 12,459,002; 12,442,929 | 12,565,568 | 12,543,278.6 | PASS |

### Plain JVM repeat from IR task

| Kernel | Checksum | Samples (ns) | Median | Mean | Check |
| --- | ---: | --- | ---: | ---: | --- |
| ir-friendly-int-loop | 2,038,221,507 | 10,026,099; 10,025,007; 10,010,770; 10,078,557; 10,084,920 | 10,026,099 | 10,045,070.6 | PASS |
| integer-loop | -5,663,617,524,014,644,874 | 10,010,942; 10,010,923; 10,009,796; 10,082,679; 10,016,023 | 10,010,942 | 10,026,072.6 | PASS |
| string-concat-hash | -124,029,673,400 | 2,059,536; 602,669; 627,116; 588,559; 759,932 | 627,116 | 927,562.4 | PASS |
| recursion | 3,055,512 | 104,236; 62,899; 63,675; 63,561; 63,606 | 63,606 | 71,595.4 | PASS |

For every row, the plain-JVM, legacy-native, and IR-task-native checksum was
identical. The harness would have failed the task on any mismatch.
