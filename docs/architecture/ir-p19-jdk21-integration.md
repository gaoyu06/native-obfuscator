# IR phase 19 and JDK 21 E2E integration

This integration starts from `origin/master` at
`2a6873107bcffbb4961e9fad936b5c3774c530ff`, after
[#127](https://github.com/gaoyu06/native-obfuscator/pull/127) and interpreter
ISA v2. It combines phase 19 from
[#128](https://github.com/gaoyu06/native-obfuscator/pull/128) with the JDK 21
behavioral E2E work from
[#126](https://github.com/gaoyu06/native-obfuscator/pull/126).

## Kept changes

- `LAND`, `LOR`, and `LXOR` lower as typed `I64` binary operations.
- `LSHL`, `LSHR`, and `LUSHR` lower with an `I64` value and `I32` count; C++
  emission applies the JVM `count & 0x3f` rule and preserves arithmetic versus
  logical right-shift behavior.
- The IR-only method clone splits a non-parameter temporary slot reused by
  reference and int local instructions. The source ASM method stays unchanged,
  and the invalid receiver-local overwrite remains rejected.
- The three `--release 21` fixtures for sequenced map views, sequenced sets, and
  virtual threads remain, together with the six-fixture, 47-method behavioral
  report. That report is evidence for this corpus, not a JDK 21 support badge.
- All focused tests from both tips remain in `IrCompilerTest`.

The overlapping `AsmToIr.java` and `IrCompilerTest.java` changes auto-merged,
and the result was audited against each tip: the long bitwise/shift lowering
and tests remain alongside the local-type clone/remap and its regression test.
The only textual merge conflict was the tips' add/add scratch `PR_BODY.md`,
which was removed; no interpreter source needed conflict resolution.

## Verification

The required focused run used:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`. Gradle's JUnit XML reports:

```text
IrCompilerTest: tests=94, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
Total: 99 tests, 0 skipped, 0 failures, 0 errors
```

A separate admission-only check built the benchmark and obfuscator JARs, then
transpiled `benchmarks/whitelist.txt` with explicit `--codegen=ir`. The command
exited 0, its complete output had no `IR codegen unsupported` or
`falling back to legacy` entry, and generated sources contain:

```text
IR codegen: benchmarks/kernels/IntegerLoopKernel.run(I)J
IR codegen: benchmarks/kernels/RecursionKernel.recurse(IJ)J
```

This was not a timing run; no performance numbers are reported.

## Delivery status / 交付状态

- **(a) Scope / 范围:** Phase 19 long bitwise and shift lowering plus the JDK
  21 fixture corpus, behavioral report, and IR-clone local-type split are
  integrated on post-#127 master. /
  已在 #127 之后的 master 上集成 phase 19 的 long 位运算与移位 lowering，
  以及 JDK 21 fixture 语料、行为报告和 IR clone 局部变量类型拆分。
- **(b) Production-ready? / 可用于生产？:** **No.** /
  **否。**
- **(c) Evidence / 证据:** The focused suites pass 99/99, both former #122
  leftover methods have direct-IR markers with no fallback log, and the
  inherited six-fixture report records 47/47 input methods on IR with exact
  stdout matches. /
  聚焦测试 99/99 通过，两个原 #122 遗留方法都有 direct-IR 标记且无 fallback
  日志；继承的六 fixture 报告记录了 47/47 输入方法进入 IR，stdout 完全一致。
- **(d) Boundary / 边界:** `--codegen` remains `legacy` by default; no
  `--backend` default changed, no `--ir-lower` option was added, and interpreter
  sources are untouched. The production goal remains incomplete. /
  `--codegen` 默认值仍为 `legacy`；未修改 `--backend` 默认值，未新增
  `--ir-lower`，且未改动 interpreter 源码。生产目标仍未完成。
