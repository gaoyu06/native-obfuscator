# IR phase 17 review

Review branch: `cursor/ir-phase17-sol-review-6d81-62ae`

Reviewed subject: `cursor/ir-compiler-phase17-6d81-2b77` at
`5a6f6097524c1fe42cd82be2425f5e6736667688` (draft PR #108), stacked on
`cursor/ir-compiler-phase16-6d81-979e` at
`dbfeb7816986ba886eb14e20c092f4f8f833a629` (draft PR #104).

## Verdict

**Accept.**

No correctness miscompile was found, so the compiler is unchanged by this
review. The admitted `DUP2`, `DUP_X2`, `DUP2_X1`, `DUP2_X2`, and `POP2`
forms are category-correct SSA stack transformations. The default remains
`legacy`; this focused result is not a production-readiness approval.

## JVM form and category audit

The implementation was checked against the JVMS operand-stack tables, with
`v1` denoting the incoming top:

- `DUP2` form 1 maps `v2(cat1), v1(cat1)` to
  `v2, v1, v2, v1`; form 2 maps `v1(cat2)` to `v1, v1`.
- `DUP_X2` form 1 maps `v3(cat1), v2(cat1), v1(cat1)` to
  `v1, v3, v2, v1`; form 2 maps `v2(cat2), v1(cat1)` to
  `v1, v2, v1`.
- `DUP2_X1` form 1 maps three category-1 values to
  `v2, v1, v3, v2, v1`; form 2 maps `v2(cat1), v1(cat2)` to
  `v1, v2, v1`.
- `DUP2_X2` implements all four forms: four category-1 values; a category-2
  `v1` above two category-1 values; two category-1 top values above a
  category-2 `v3`; and two category-2 values. Every output order matches the
  status table and the JVMS table.
- `POP2` removes either two category-1 values or one category-2 value.

`IrType.I32`, `F32`, and `REFERENCE` report one JVM slot; `I64` and `F64`
report two. Both analysis stacks store one list entry per `IrValue`, not one
entry per slot. Consequently the transformation duplicates, moves, or removes
a category-2 value as a unit and cannot split it into category-1 entries.

## Shared validation and side effects

The stack-type pass and SSA value lowering both call the same generic
`applyWideStackOperation` implementation. Their only adapter maps either an
`IrType` or an `IrValue` to `IrType.getJvmSlots()`, so their legal-form
decisions and output ordering cannot drift independently.

For every accepted path, all operands needed to identify a legal form are read
and category-checked before the first `List` insertion or removal. Underflow
and illegal category sequences throw `UnsupportedIrConstructException` at the
shuffle opcode. Whole-method instruction admission also runs before stack
analysis, so the retained non-`int` `NEWARRAY` fallback is detected before any
IR lowering.

`IrMethodCompiler` completes frontend construction and C++ body emission
before `MethodShellEmitter.beginIr`. Rejection therefore leaves `ACC_NATIVE`,
generated output, native metadata, and class/string/field/method caches
unchanged. The phase-17 fallback regression exercises the admitted shuffles,
including `SWAP` followed by `DUP2_X1`, before rejecting `NEWARRAY T_BYTE`.

The shuffle branch only changes the in-memory stack list. It does not call
`IrMethod.newInstructionValue`, add an `IrInstruction`, allocate a C++ local,
or emit JNI. The SSA-identity assertions cover the output stack values, and
the emitted-body assertions reject `env->` calls.

## Measured pattern and retained behavior

The measured opcode-93 sequence starts with
`REFERENCE, I32, F32`, applies category-1 `SWAP`, then applies `DUP2_X1`
form 1. The asserted output is `F32, I32, REFERENCE, F32, I32`, matching the
two transformations in sequence.

The focused suite retains the phase-9 through phase-16 regression methods.
Source and regression inspection also confirms:

- `Main` keeps `--codegen` at `defaultValue = "legacy"`;
- the no-mode public API and `MethodProcessor.shouldProcess` overloads keep
  selecting `CodegenMode.LEGACY`;
- `obfuscator/src/main/resources/sources/cppsnippets.properties` remains
  present;
- only `NEWARRAY T_INT` is admitted by the direct IR frontend, while other
  primitive `NEWARRAY` forms and `MULTIANEWARRAY` still fall back.

## Re-run evidence

The independent review run and XML-derived counts will be recorded after the
pre-test review commit is pushed.

## Ship-readiness / 交付准备度

- **(a) Verdict / 结论:** Accept the Phase 17 stack-transform implementation;
  no correctness miscompile was found. / 接受 Phase 17 栈变换实现；未发现正确性
  误编译。
- **(b) Ship-ready? / 可直接发布？:** **No.** Focused compiler and syntax
  verification does not replace full supported-platform CI and native runtime
  parity testing. / **否。** 聚焦编译器与语法验证不能替代所有受支持平台的
  CI 和原生运行时一致性测试。
- **(c) Compiler changed? / 编译器是否改动？:** No. This review is
  documentation-only. / 否。本次审查仅改动文档。
- **(d) Integration / 集成:** Keep the stack based on draft PR #104, preserve
  the `legacy` default and phase-9 through phase-16 regressions, and keep
  unsupported array forms on per-method fallback. / 保持基于草稿 PR #104 的
  堆叠关系，保留 `legacy` 默认值及 phase-9 至 phase-16 回归，并让不支持的
  数组形式继续按方法回退。
