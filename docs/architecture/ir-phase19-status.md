# IR phase 19 status

Phase 19 extends the opt-in direct Java bytecode → typed CFG IR → C++/JNI
compiler with the complete JVM long bitwise and shift family: `LAND`, `LOR`,
`LXOR`, `LSHL`, `LSHR`, and `LUSHR`. The base is current `origin/master` at
`e997d71c7525a4c607e29b6eb1ae9140a72dfd22`, including phase 18 through
[#118](https://github.com/gaoyu06/native-obfuscator/pull/118).

`LAND`, `LOR`, and `LXOR` extend the two-`I64` `LongBinary` node. Long shifts
use a separate typed node with an `I64` value and `I32` count. Emission masks
the count with `0x3f`; left shift and logical right shift use `uint64_t`, while
arithmetic right shift uses `int64_t`. Results return through the existing
`jlong` carrier.

The CLI and API default remains `legacy`. Unsupported methods retain
per-method legacy fallback. This increment does not change the evaluator,
reader, `--ir-lower`, classfile version handling, or constructor restoration.

## Verification

The focused compiler and mode suites are run with:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result on 2026-08-29: `BUILD SUCCESSFUL`. Gradle's JUnit XML reports:

```text
IrCompilerTest: tests=93, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
Total: 98 tests, 0 skipped, 0 failures, 0 errors
```

The phase-19 tests cover all six operations, the `I64` value plus `I32` count
shape, `count & 0x3f`, logical versus arithmetic right shift, and a
`Long.MAX_VALUE << 65` masking and wrapping case. The generated C++ smoke
translation unit also includes the new operations and passed its
`g++ -std=c++17 -fsyntax-only` gate.

### Current-master benchmark-kernel admission

The current-master benchmark JAR was passed through the CLI with
`--codegen=ir` and `benchmarks/whitelist.txt`. The transpile command exited
zero. Its complete log contains no `IR codegen unsupported` or
`falling back to legacy` entry. Generated sources contain direct-IR markers
for both previously rejected methods:

```text
IR codegen: benchmarks/kernels/IntegerLoopKernel.run(I)J
IR codegen: benchmarks/kernels/RecursionKernel.recurse(IJ)J
```

The integer-loop source emits `value >>> 17` through a `uint64_t` right shift
with `& 63`; the recursion source emits its recursive result mix through
`uint64_t` `^`. This was an admission check, not a timing run:
`:obfuscator:bench` was not re-run and no performance samples are reported.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for `LAND`, `LOR`,
  `LXOR`, `LSHL`, `LSHR`, and `LUSHR`, including JVM long shift-count
  masking and unsigned logical-shift emission. /
  直接 typed-CFG IR 支持 `LAND`、`LOR`、`LXOR`、`LSHL`、`LSHR` 和
  `LUSHR`，包括 JVM long 移位计数掩码与无符号逻辑右移发射。
- **(b) Ship-ready? / 可直接发布？:** **No.** /
  **否。**
- **(c) Review focus / 审查重点:** Check the `I64`/`I32` long-shift operand
  split, `count & 0x3f`, `uint64_t` wrapping behavior, and the distinction
  between `LSHR` and `LUSHR`. /
  请重点审查 long 移位的 `I64`/`I32` 操作数区分、`count & 0x3f`、
  `uint64_t` 回绕行为，以及 `LSHR` 与 `LUSHR` 的差异。
- **(d) Integration / 集成:** Keep `--codegen` defaulting to `legacy` and
  retain per-method fallback; do not combine this increment with evaluator,
  interpreter, or `--ir-lower` changes. /
  保持 `--codegen` 默认值为 `legacy` 并保留逐方法 fallback；不要将本增量
  与 evaluator、interpreter 或 `--ir-lower` 变更合并。
