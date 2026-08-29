# Fable review of PR #128 (IR phase 19: long bitwise and shifts) / PR #128（IR 第 19 阶段：long 位运算与移位）Fable 审查

**Verdict / 结论: accept with nits (docs-only) / 接受，附非阻塞性意见（仅文档变更）**

This branch contains only `docs/reviews/ir-phase19-fable.md` and this body on
top of the reviewed tip `720205d3645f49d4fabcf6d98bb13db3cfbdf733` of
`cursor/ir-compiler-phase19-6d81`. No production code is changed. /
本分支在被审查提交 `720205d3645f49d4fabcf6d98bb13db3cfbdf733`
（`cursor/ir-compiler-phase19-6d81`）之上仅新增
`docs/reviews/ir-phase19-fable.md` 与本说明，不改动任何生产代码。

## Findings / 审查结论

- `LAND`/`LOR`/`LXOR` are typed as two `I64` operands via `LongBinary` with
  constructor-enforced `requireI64`. / `LAND`/`LOR`/`LXOR` 通过 `LongBinary`
  以两个 `I64` 操作数建模，构造函数用 `requireI64` 强制类型。
- `LSHL`/`LSHR`/`LUSHR` use the new `LongShift` node with an `I64` value and
  an `I32` count, popped in correct JVM order (count first, then value). /
  `LSHL`/`LSHR`/`LUSHR` 使用新的 `LongShift` 节点，`I64` 值加 `I32`
  计数，按正确的 JVM 顺序出栈（先计数，后值）。
- Shift counts are masked with `((uint32_t) count & 63)`, matching the JVM
  `count & 0x3f` rule. / 移位计数按 `((uint32_t) count & 63)` 掩码，符合 JVM
  `count & 0x3f` 规则。
- `LSHR` emits an arithmetic shift on `int64_t`; `LUSHR` emits a logical
  shift on `uint64_t` and does not sign-extend. A nine-case differential
  check of the emitted patterns against Java semantics (including negative
  values, negative counts, and counts ≥ 64) passed on g++ 13.3.0. /
  `LSHR` 在 `int64_t` 上发射算术右移；`LUSHR` 在 `uint64_t` 上发射逻辑右移，
  不做符号扩展。针对发射模式与 Java 语义的九用例差分验证（含负值、负计数、
  计数 ≥ 64）在 g++ 13.3.0 上全部通过。
- The `--codegen` default remains `legacy` and per-method fallback is
  untouched. / `--codegen` 默认值仍为 `legacy`，逐方法 fallback 未改动。

## Tests run by this reviewer / 审查者实际运行的测试

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result on 2026-08-29: `BUILD SUCCESSFUL`; `IrCompilerTest` tests=93 and
`CodegenModeTest` tests=5, with 0 skipped, 0 failures, 0 errors (98 total). /
2026-08-29 结果：`BUILD SUCCESSFUL`；`IrCompilerTest` 93 项、
`CodegenModeTest` 5 项，共 98 项，0 跳过、0 失败、0 错误。

The benchmark-kernel admission claim was independently reproduced: the CLI
transpile with `--codegen=ir` exited zero with no fallback log entry, and both
`IntegerLoopKernel.run(I)J` and `RecursionKernel.recurse(IJ)J` carry direct-IR
markers. No timing benchmark was run. / 基准内核准入声明已独立复现：
`--codegen=ir` 的 CLI 转译以退出码 0 结束，日志无 fallback 记录，
`IntegerLoopKernel.run(I)J` 与 `RecursionKernel.recurse(IJ)J`
均带直接 IR 标记。未运行任何计时基准。

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Independent (Fable) review of PR #128 at tip
  `720205d`: long bitwise (`LAND`/`LOR`/`LXOR`) and long shift
  (`LSHL`/`LSHR`/`LUSHR`) admission into the opt-in direct IR compiler,
  including operand typing, `0x3f` masking, and logical-vs-arithmetic
  right-shift emission, plus one review document. /
  对 PR #128 提交 `720205d` 的独立（Fable）审查：long 位运算
  （`LAND`/`LOR`/`LXOR`）与 long 移位（`LSHL`/`LSHR`/`LUSHR`）进入可选
  直接 IR 编译器的准入，含操作数类型、`0x3f` 掩码、逻辑与算术右移的
  发射区分，另附一份审查文档。
- **(b) Ship-ready? / 可直接发布？:** **No.** This is a review artifact on an
  incremental opt-in compiler branch; it does not make the project
  ship-ready, and it does not claim JDK 17/21 input support or that
  requirement 7 is met. / **否。** 本分支是针对渐进式可选编译器分支的审查
  产物，不代表项目可直接发布，也不声明支持 JDK 17/21 输入或满足需求 7。
- **(c) Review focus / 审查重点:** Confirm the checklist findings above,
  especially the `I64`/`I32` shift operand split, `count & 0x3f`, and that
  `LUSHR` does not sign-extend; the three recorded nits are non-blocking. /
  请确认上述核对结论，特别是移位的 `I64`/`I32` 操作数区分、
  `count & 0x3f`，以及 `LUSHR` 不做符号扩展；记录的三条意见均不阻塞。
- **(d) Integration / 集成:** Land this review branch after (or together
  with) PR #128; keep `--codegen` defaulting to `legacy`; do not merge
  PR #126 as part of this review. /
  请在 PR #128 之后（或随其一起）合入本审查分支；保持 `--codegen`
  默认为 `legacy`；不要将 PR #126 作为本审查的一部分合并。
