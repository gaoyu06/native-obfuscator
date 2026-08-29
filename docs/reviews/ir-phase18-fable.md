# IR phase 18 second independent review (Fable) / IR phase 18 第二次独立审查（Fable）

Reviewed tip: `b78d6d7c74b11c416f5703df89ad6b0c1532aec2`
(`cursor/ir-compiler-phase18-6d81-a7ae`, draft PR #114)

Reviewed base: `5a6f6097524c1fe42cd82be2425f5e6736667688`
(`cursor/ir-compiler-phase17-6d81-2b77`, PR #108)

This is the second independent compiler-correctness review of phase 18,
performed after the first accept in `docs/reviews/ir-phase18-sol.md`
(PR #116). It reviews ordinary Java-to-native compiler behavior only. It
does not claim JDK 17 runtime, semantic, or product support, and it does
not include or endorse the sibling JDK 17 runtime-fix stack (#113/#115/#117).

本文是对 phase 18 的第二次独立编译器正确性审查，在
`docs/reviews/ir-phase18-sol.md`（PR #116）第一次接受之后进行。本审查仅
针对普通 Java 到 native 编译器行为，不声明 JDK 17 runtime、语义或产品
支持，也不包含或背书同级 JDK 17 runtime-fix 分支（#113/#115/#117）。

## Verdict / 结论

**Accept (docs-only review note).** The complete diff of
`b78d6d7` against `5a6f609` was read file by file
(`AsmToIr.java`, `IrNodes.java`, `IrValue.java`, `IrMethod.java`,
`IrCppEmitter.java`, `CppAst.java`, `CfgBuilder.java`, both test files, and
the status note). No compiler defect was found, and the required focused
rerun passed with the counts recorded below.

**接受（仅增加审查文档）。** 已逐文件阅读 `b78d6d7` 相对 `5a6f609` 的
完整差异（`AsmToIr.java`、`IrNodes.java`、`IrValue.java`、`IrMethod.java`、
`IrCppEmitter.java`、`CppAst.java`、`CfgBuilder.java`、两个测试文件及状态
文档）。未发现编译器缺陷，要求的聚焦复跑已通过，计数见下文。

## Defect hunt results / 缺陷排查结果

Each risk named for this phase was checked against the actual code:

- **`[Z` vs `[B` JNI family mixups — none found.** Every `NEWARRAY` atype
  maps to its exact JNI allocation and region family
  (`T_BOOLEAN`→`Boolean`, `T_BYTE`→`Byte`, …, `T_DOUBLE`→`Double`), and the
  `create_array_value` template specializations match the ASM sort
  constants 1–8 exactly (`<1>`→`NewBooleanArray`, `<3>`→`NewByteArray`,
  `<5>`→`NewIntArray`, `<8>`→`NewDoubleArray`). For `BALOAD`/`BASTORE`, a
  verifier-known `[Z` or `[B` descriptor selects the exact family; when the
  descriptor is unknown the emitter honestly calls the retained
  `utils::baload`/`utils::bastore` runtime discriminators instead of
  guessing one family.
- **Descriptor soundness.** The only reference values that can still carry a
  null descriptor are caught exceptions (never array-typed under the
  verifier) and `ACONST_NULL` (null at runtime, preceded by the NPE guard
  before any array access). Phi refinement from partial incoming
  descriptors therefore cannot select a wrong JNI family for verifiable
  input, and conflicting incoming descriptors keep the phi unresolved
  rather than picking a side. `refineReferenceDescriptor` is only invoked
  on still-null descriptors, so its conflict throw is unreachable.
- **Narrowing rules.** Boolean stores mask with `& 1` (JVMS `bastore`
  low-bit truncation for `boolean[]`); byte, char, and short stores cast to
  `jbyte`, `jchar`, `jshort`; loads widen back through the signed
  (`jbyte`/`jshort`) or unsigned (`jchar`/`jboolean`) carrier, matching
  JVM sign/zero extension.
- **Missing `NegativeArraySizeException` — none.** Every primitive
  `NEWARRAY` emits a `length < 0` guard that throws the pending exception
  and takes the block's exceptional exit before any JNI call.
  `MULTIANEWARRAY` checks every supplied dimension before allocation, as
  JVMS requires.
- **`MULTIANEWARRAY` dimension order and partial dims — correct.** Operands
  are popped innermost-first into slots `dims-1 … 0`, so index 0 is the
  outermost count, which is exactly how the retained recursive helpers
  consume `sizes`. The emitter passes the descriptor's total dimension
  count and the instruction's supplied count separately — identical to the
  legacy `MultiANewArrayHandler` (`count`/`required_count`) — and the
  helper's `required_count == 1` stop leaves inner references null for
  partial-dimension allocations.
- **Phi descriptor loss — none.** `propagateReferenceDescriptors` runs a
  fixpoint over reference phis and reference-array loads after phi
  connection and before emission.
- **Category-2 split — none.** `LALOAD`/`LASTORE` and `DALOAD`/`DASTORE`
  use single `I64`/`F64` IR values; the abstract stack model pops/pushes
  the element carrier and the region temporaries are `jlong`/`jdouble`.
- **Default-codegen flip — none.** `Main` still declares
  `defaultValue = "legacy"`; `CodegenModeTest` verifies the default, the
  no-mode `MethodProcessor.shouldProcess` overload, and explicit
  `--codegen=legacy` after phase 18.
- **Dishonest fallbacks — none.** Invalid `NEWARRAY` atypes, malformed or
  over-dimensioned `MULTIANEWARRAY` descriptors, and non-primitive,
  non-object base elements reject the whole method during validation,
  before any shell mutation; the fallback regression observes rejection at
  `INVOKEDYNAMIC` (opcode 186) with output, caches, and method access
  unchanged.

针对本阶段列出的每一项风险均已对照实际代码检查：`[Z`/`[B` 未发现 JNI
family 混用（descriptor 已知时选择精确 family，未知时诚实调用运行时
判别 helper 而非猜测）；descriptor 传播对可验证输入是可靠的（仅捕获
异常与 `ACONST_NULL` 仍无 descriptor，二者均不可能是数组或在访问前触发
NPE）；窄化规则符合 JVMS（boolean 存储 `& 1`，byte/char/short 分别转
`jbyte`/`jchar`/`jshort`）；`NegativeArraySizeException` 无遗漏；
`MULTIANEWARRAY` 维度顺序正确且部分维度时内层保持 null，`count`/
`required_count` 语义与 legacy handler 一致；phi descriptor 无丢失；
category-2 无拆分；默认 codegen 未翻转；fallback 在 mutation 前完成且
无虚假成功。

## Focused verification / 聚焦验证

Command actually run on this machine at the reviewed tip:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --console=plain
```

Result: `BUILD SUCCESSFUL`. Counts read directly from the JUnit XML in
`obfuscator/build/test-results/test/`:

```text
IrCompilerTest:  tests=88, skipped=0, failures=0, errors=0
CodegenModeTest: tests=4,  skipped=0, failures=0, errors=0
Total: 92 tests, 0 skipped, 0 failures, 0 errors
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable()` executed (no
`<skipped>` element), so the emitted translation unit — including all eight
primitive round trips and the rectangular multi-array methods — passed the
real g++ syntax gate during this rerun.

上述命令在被审查 tip 上实际执行，`BUILD SUCCESSFUL`；JUnit XML 计数为
`IrCompilerTest` 88 通过、`CodegenModeTest` 4 通过，共 92 通过、0 跳过、
0 失败、0 错误。g++ 语法门测试真实执行且未被跳过。

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Second independent correctness review of phase 18:
  opt-in `--codegen=ir` lowering of every primitive `NEWARRAY`, the
  matching primitive `*ALOAD`/`*ASTORE` family, and rectangular
  primitive/reference `MULTIANEWARRAY`, with pending-exception routing and
  pre-mutation fallback. Docs-only accept; no compiler change. /
  对 phase 18 的第二次独立正确性审查：opt-in `--codegen=ir` 下全部
  primitive `NEWARRAY`、对应的 primitive `*ALOAD`/`*ASTORE` 族及矩形
  primitive/reference `MULTIANEWARRAY` 的 lowering，含 pending exception
  路由与 mutation 前 fallback。仅文档接受，无编译器改动。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** The IR path
  remains opt-in and incomplete (`INVOKEDYNAMIC` and `ConstantDynamic`
  remain unsupported), and no JDK 17 runtime or product support is claimed. /
  **否——尚未达到可发布状态。** IR 路径仍为 opt-in 且不完整
  （`INVOKEDYNAMIC` 与 `ConstantDynamic` 仍不支持），且不声明 JDK 17
  runtime 或产品支持。
- **(c) Review focus / 审查重点:** For later phases, keep verifying `[Z`
  versus `[B` descriptor flow at new descriptor sources, the `& 1` boolean
  and `jbyte`/`jchar`/`jshort` narrowing rules, outermost-first dimension
  order plus null inner arrays for partial `MULTIANEWARRAY`, and that
  unresolved byte/boolean carriers keep using the runtime discriminator. /
  后续阶段请继续核查新增 descriptor 来源处 `[Z` 与 `[B` 的流转、boolean
  的 `& 1` 及 `jbyte`/`jchar`/`jshort` 窄化规则、最外层优先的维度顺序与
  部分维度时内层为 null，以及未解析的 byte/boolean carrier 是否仍走
  运行时判别 helper。
- **(d) Integration / 集成:** This branch stacks on
  `cursor/ir-compiler-phase18-6d81-a7ae` at `b78d6d7`, whose base is PR
  #108 at `5a6f609`, not `master`. Re-run the focused suite with
  `CC=gcc CXX=g++` before integrating. Preserve the `legacy` default,
  per-method fallback, snippet resources, and phase-9 through phase-17
  regressions. The #110 JDK 17 figure remains admission-only evidence; do
  not merge the sibling JDK 17 runtime-fix stack (#113/#115/#117) as part
  of this review. /
  本分支叠加于 `b78d6d7` 的 `cursor/ir-compiler-phase18-6d81-a7ae`，其
  基线是 `5a6f609` 的 PR #108 而非 `master`。集成前请用
  `CC=gcc CXX=g++` 重新运行聚焦测试；保留 `legacy` 默认值、按方法
  fallback、snippet 资源及 phase-9 至 phase-17 回归。#110 的 JDK 17 数据
  仅是 admission 证据；不要将同级 JDK 17 runtime-fix 分支
  （#113/#115/#117）作为本审查的一部分合并。
