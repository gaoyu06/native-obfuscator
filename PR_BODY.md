# IR phase 5 — Fable review branch

This branch is the Fable design author's review of the Sol phase-5 slice (PR #40,
`cursor/ir-compiler-phase5-6d81`): integer divide/remainder, `int` array
allocation, and static `int` field access. It is documentation only; it adds
`docs/architecture/ir-phase5-fable-review.md` and this PR body. No compiler code
changed, because the review found no correctness blocker to fix.

本分支是 Fable 设计作者对 Sol phase-5 切片（PR #40，
`cursor/ir-compiler-phase5-6d81`：整数除/余、`int` 数组分配、静态 `int` 字段访问）的
审阅，**仅含文档**：新增 `docs/architecture/ir-phase5-fable-review.md` 与本 PR 说明。
未改动任何编译器代码，因为审阅未发现需修复的正确性阻塞项。

## (a) Scope / 改动范围

- Ordinary Java-bytecode-to-JNI-C++ compiler review only: typed IR, CFG and
  exception edges, structured C++ emit, and JNI pending-exception lifetime.
  Packing / analysis-resistance / protection are out of scope and not discussed.
- Reviewed every changed file under
  `obfuscator/src/main/java/by/radioegor146/ir/**` plus `IrCompilerTest`, against
  design of record `docs/architecture/ir-compiler.md` (§4 types, §5 exceptions,
  §6 JNI object model) and `docs/architecture/ir-phase5-status.md`.
- Independently re-ran the focused test suite and inspected the JUnit XML,
  confirmed the g++ compile-smoke executed (not skipped), independently
  recompiled the emitted translation unit, and read the emitted C++ for
  `divRem`, `catchDivide`, `allocate`, and `setAndGetCounter` out of that
  g++-accepted file.

仅为常规的 Java 字节码 → JNI C++ 编译器审阅：typed IR、CFG 与异常边、结构化 C++ 发射、
JNI pending-exception 生命周期；不涉及加壳/反分析/保护。已按设计基准 `ir-compiler.md`
（§4/§5/§6）与 `ir-phase5-status.md` 阅读全部 `ir/**` 改动文件与 `IrCompilerTest`，并
独立重跑测试、核对 JUnit XML、确认 g++ 冒烟未跳过、独立重编译翻译单元、直接阅读四个新增
方法所生成的 C++。

## (b) Ship-ready? / 是否可直接上线

**No.** Phase 5 is an intentionally limited slice: only `T_INT` `NEWARRAY`, only
descriptor-`I` static fields, and per-method fallback for switches, object and
other array creation, constructors, wide carriers, broader invoke/field/array
shapes, and many stack/control opcodes. It lacks broad opcode coverage and full
JVM runtime-parity validation. It is safe to carry behind the non-default
`--codegen=ir` flag while the migration continues.

**否。** phase 5 是有意受限的切片：仅 `T_INT` `NEWARRAY`、仅描述符 `I` 的静态字段，其余
（switch、对象与其他数组创建、构造器、wide carrier、更广的调用/字段/数组形态及许多栈与
控制 opcode）仍按方法回退；尚缺完整 opcode 覆盖与 JVM 运行时等价性验证。可在非默认的
`--codegen=ir` 开关后随迁移继续推进。

## (c) Review required? / 是否需要 review

**This branch IS the review.** The verdict is **accept-with-nits**; the full
requirement-by-requirement analysis, the g++ evidence, the deltas from the
design, and the non-blocking nits are in
`docs/architecture/ir-phase5-fable-review.md`.

**本分支即为 review。** 结论为 **accept-with-nits**；逐项分析、g++ 证据、与设计的差异
说明及不阻塞的小瑕疵详见 `docs/architecture/ir-phase5-fable-review.md`。

## (d) Preconditions for a human / 人工前置条件

1. Read this on top of the stack: base is `cursor/ir-phase4-fable-review-6d81`
   (`564589c…`); the subject implementation is PR #40
   (`cursor/ir-compiler-phase5-6d81`). This review branch is documentation-only
   and opens no GitHub PR.
   以本栈为基线阅读：base 为 `cursor/ir-phase4-fable-review-6d81`（`564589c…`），被审
   对象为 PR #40（`cursor/ir-compiler-phase5-6d81`）；本审阅分支仅含文档，且不开 GitHub PR。
2. Reproduce the counts:
   `./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
   → `IrCompilerTest` 22/22, `CodegenModeTest` 2/2, 24 total, 0 skipped / 0
   failures / 0 errors. Inspect the JUnit XML rather than trusting console output.
   复现计数并核对 JUnit XML：两组分别为 22/22 与 2/2，共 24 个，0 跳过 / 0 失败 / 0 错误。
3. With a toolchain present, confirm
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is not skipped and
   `g++ -std=c++17 -fsyntax-only` actually compiles the emitted 18-method TU;
   `Assumptions.assumeTrue(...)` will skip it if g++/jni.h are missing, so a
   toolchain-less run is not evidence.
   在有工具链时确认 g++ 冒烟未 skip 且真实编译 18-method 翻译单元；缺工具链的运行不构成证据。
4. Spot-check the emitted C++: `IDIV`/`IREM` check the divisor before `/`/`%`,
   raise `ArithmeticException` into the shared `IR_CATCH_n` when protected (no
   silent `return 0`), and guard `Integer.MIN_VALUE / -1` and `% -1` (rendered
   `((jint) 0x80000000U)`, no `juint`); `NEWARRAY T_INT` checks negative length,
   null result, and `ExceptionCheck`; static `I` fields use `GetStaticFieldID`
   and `GetStatic`/`SetStaticIntField` on the existing `cclasses`/`cfields`.
   抽查生成的 C++：`IDIV`/`IREM` 先查除数再求值、受保护时抛 `ArithmeticException` 到
   共享 `IR_CATCH_n`（不静默 `return 0`）、并特判 `MIN/-1`（发射为 `((jint) 0x80000000U)`，
   无 `juint`）；`NEWARRAY T_INT` 检查负长度/空返回/`ExceptionCheck`；静态 `I` 字段使用
   `GetStaticFieldID` 与 `GetStatic`/`SetStaticIntField` 并复用现有 `cclasses`/`cfields`。
5. Confirm fallback-before-mutation independently: a non-`I` static field is
   rejected in `AsmToIr.build(...)` before cache allocation and before `beginIr`
   mutates `output`/`nativeMethods`/`ACC_NATIVE`
   (`rejectsNonIntStaticFieldBeforeMutation`); default codegen is still `legacy`
   and the snippet path is intact.
   独立确认变更前回退：非 `I` 静态字段在 `AsmToIr.build(...)` 被拒，早于缓存分配与
   `output`/`nativeMethods`/`ACC_NATIVE` 改动；默认仍为 `legacy`，旧片段路径完好。
