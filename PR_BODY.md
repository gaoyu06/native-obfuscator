# IR phase 4 — Fable review branch

This branch is the Fable design author's review of the Sol phase-4 exception
slice (PR #36, `cursor/ir-compiler-phase4-6d81`). It is documentation only; it
adds `docs/architecture/ir-phase4-fable-review.md` and this PR body. No compiler
code changed, because the review found no correctness blocker to fix.

本分支是 Fable 设计作者对 Sol phase-4 异常切片（PR #36，
`cursor/ir-compiler-phase4-6d81`）的审阅，**仅含文档**：新增
`docs/architecture/ir-phase4-fable-review.md` 与本 PR 说明。未改动任何编译器代码，
因为审阅未发现需修复的正确性阻塞项。

## (a) Scope / 改动范围

- Ordinary Java-bytecode-to-JNI-C++ compiler review only: CFG, SSA, exception
  edges, typed IR, structured C++ emit, and JNI pending-exception lifetime.
  Packing / analysis-resistance / protection are out of scope and not discussed.
- Reviewed every changed file under
  `obfuscator/src/main/java/by/radioegor146/ir/**` plus `IrCompilerTest`, against
  design of record `docs/architecture/ir-compiler.md` §5 and
  `docs/architecture/ir-phase4-status.md`.
- Independently re-ran the focused test suite, confirmed the g++ compile-smoke
  executed (not skipped), independently recompiled the emitted translation unit,
  and read the emitted C++ for the four new exception fixtures out of that
  g++-accepted file.

仅为常规的 Java 字节码 → JNI C++ 编译器审阅：CFG、SSA、异常边、typed IR、结构化 C++
发射、JNI pending-exception 生命周期；不涉及加壳/反分析/保护。已按设计基准
`ir-compiler.md` §5 与 `ir-phase4-status.md` 阅读全部 `ir/**` 改动文件与
`IrCompilerTest`，并独立重跑测试、确认 g++ 冒烟未跳过、独立重编译翻译单元、直接阅读
四个异常样例所生成的 C++。

## (b) Ship-ready? / 是否可直接上线

**No.** Phase 4 is an intentionally limited exception slice: handlers with a
normal predecessor, `monitor`/`finally`/nested-unsupported bodies, switches, wide
carriers, and most opcodes still fall back per method. It lacks broad opcode
coverage and full JVM runtime-parity validation. It is safe to carry behind the
non-default `--codegen=ir` flag while the migration continues.

**否。** phase 4 是有意受限的异常切片：带正常前驱的 handler、`monitor`/`finally`/
嵌套不支持体、switch、wide carrier 及大多数 opcode 仍按方法回退；尚缺完整 opcode 覆盖
与 JVM 运行时等价性验证。可在非默认的 `--codegen=ir` 开关后随迁移继续推进。

## (c) Review required? / 是否需要 review

**This branch IS the review.** The verdict is **accept-with-nits**; the full
requirement-by-requirement analysis, the g++ evidence, the deltas from design §5,
and the non-blocking nits are in `docs/architecture/ir-phase4-fable-review.md`.

**本分支即为 review。** 结论为 **accept-with-nits**；逐项分析、g++ 证据、与设计 §5 的
差异说明及不阻塞的小瑕疵详见 `docs/architecture/ir-phase4-fable-review.md`。

## (d) Preconditions for a human / 人工前置条件

1. Read this on top of the stack: base is `cursor/ir-phase3-fable-review-6d81`;
   the subject implementation is PR #36 (`cursor/ir-compiler-phase4-6d81`). This
   review branch is documentation-only and opens no GitHub PR (403).
   以本栈为基线阅读：base 为 `cursor/ir-phase3-fable-review-6d81`，被审对象为 PR #36；
   本审阅分支仅含文档，且不开 GitHub PR（403）。
2. Reproduce the counts:
   `./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
   → `IrCompilerTest` 17/17, `CodegenModeTest` 2/2, 0 skipped / 0 failures / 0
   errors. Inspect the JUnit XML rather than trusting console output.
   复现计数并核对 JUnit XML。
3. With a toolchain present, confirm
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is not skipped and
   `g++ -std=c++17 -fsyntax-only` actually compiles the emitted TU; the assertions
   `Assumptions.assumeTrue(...)` will skip it if g++/jni.h are missing, so a
   toolchain-less run is not evidence.
   在有工具链时确认 g++ 冒烟未 skip 且真实编译；缺工具链的运行不构成证据。
4. Spot-check the emitted C++: a protected `IALOAD` `ExceptionCheck` must jump to
   a shared `IR_CATCH_n` and the catch must run (no silent `return 0`); unmatched
   catch rethrows via `env->Throw`; catch-all has no `IsInstanceOf`; no `goto`
   crosses an initialized automatic; only real JNI types appear.
   抽查生成的 C++：受保护 `IALOAD` 的 `ExceptionCheck` 须跳到共享 `IR_CATCH_n` 且执行
   catch（不得静默 `return 0`）；未匹配重抛；catch-all 无 `IsInstanceOf`；`goto` 不跨越
   已初始化自动变量；仅出现真实 JNI 类型。
5. Confirm fallback-before-mutation independently: `UnsupportedIrConstructException`
   only from `AsmToIr.build(...)`, before cache allocation and before `beginIr`
   mutates `output`/`nativeMethods`/`ACC_NATIVE`; default codegen is still
   `legacy` and the snippet path is intact.
   独立确认变更前回退：异常仅来自 `AsmToIr.build(...)`，早于缓存分配与
   `output`/`nativeMethods`/`ACC_NATIVE` 改动；默认仍为 `legacy`，旧片段路径完好。
