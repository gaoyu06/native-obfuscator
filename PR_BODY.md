# docs: Sol review of IR phase 20

## English

Independent Sol review of compiler PR
`cursor/ir-compiler-phase20-6d81` at
`257b1531cfbfd83f2ff0b322a2331ae90ea4001f`, based on
`76ebeddb005e01033523384275c8c0c1641ada81`.

Verdict: **Accept.** No compiler correctness bug was found; this branch adds
only the independent review note.

- **(a) Scope:** Review the opt-in `--codegen=ir` lowering of `LDIV`, `LREM`,
  and `LNEG`: typed nodes, JVM stack order, exceptional CFG edges,
  divide-by-zero transfer, `Long.MIN_VALUE / -1`, and wrapping negation.
- **(b) Ship-ready?** **No.** This remains one opt-in compiler increment and
  does not establish broad runtime or JDK support.
- **(c) Review result:** `LongDivRem` is a dedicated `I64`/`I64`/`I64` node;
  `LongUnary.NEGATE` is strictly `I64`; the frontend pops right then left;
  `LDIV`/`LREM` (but not `LNEG`) are potentially throwing; emitted C++ uses
  `utils::throw_re` plus `exceptionalExit`, an overflow guard, signed
  `int64_t` division/remainder, and `uint64_t` negation. The replacement
  mutation-guard opcode is `LCMP`, which remains unsupported.
- **(d) Integration:** Stack only on `cursor/ir-compiler-phase20-6d81`.
  Preserve the `legacy` codegen default and keep evaluator, interpreter, and
  CLI-default changes out of this review.

Required focused verification:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Independent result and JUnit XML counts: pending the post-commit rerun.

## 中文

本分支对编译器 PR `cursor/ir-compiler-phase20-6d81` 的提交
`257b1531cfbfd83f2ff0b322a2331ae90ea4001f` 进行独立 Sol 审查；基线为
`76ebeddb005e01033523384275c8c0c1641ada81`。

结论：**接受。** 未发现编译器正确性缺陷；本分支仅新增独立审查记录。

- **(a) 范围：** 审查 opt-in `--codegen=ir` 对 `LDIV`、`LREM`、`LNEG` 的
  lowering，包括 typed node、JVM stack 顺序、异常 CFG edge、除零转移、
  `Long.MIN_VALUE / -1` 及取负回绕。
- **(b) 可直接发布？** **否。** 这仍是单个 opt-in 编译器增量，不代表广泛
  runtime 或 JDK 支持。
- **(c) 审查结果：** `LongDivRem` 是独立的 `I64`/`I64`/`I64` 节点；
  `LongUnary.NEGATE` 严格使用 `I64`；frontend 先弹右再弹左；
  `LDIV`/`LREM`（不含 `LNEG`）属于潜在抛出指令；生成的 C++ 使用
  `utils::throw_re` 与 `exceptionalExit`、溢出保护、有符号 `int64_t`
  除法/余数及 `uint64_t` 取负。替换后的 mutation guard opcode 为仍不支持
  的 `LCMP`。
- **(d) 集成：** 仅堆叠在 `cursor/ir-compiler-phase20-6d81`；保持
  `legacy` codegen 默认值，不在本审查中加入 evaluator、interpreter 或 CLI
  默认值变更。

独立复跑结果及 JUnit XML 数量：等待提交后的聚焦复跑。
