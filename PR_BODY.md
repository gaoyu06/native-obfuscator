# Admit IF_ACMPEQ / IF_ACMPNE on the typed CFG IR path

## (a) Scope

Admit JVM `IF_ACMPEQ` and `IF_ACMPNE` on the opt-in typed CFG IR path
(`--codegen=ir`, `--ir-lower=direct`) so methods that branch on reference
identity no longer fall back to the legacy snippet generator. This is the next
IR-admission leftover after `LCMP` landed (#153 / #156).

Changes:

- New `IrNodes.ReferenceCompareBranch` terminator: two `REFERENCE` operands
  (`left`, `right`), an `EQ`/`NE` condition, and true/false targets. It is
  distinct from the null-only `ReferenceBranch` and from the i32 `Branch`, and
  it never throws (identity comparison).
- `AsmToIr` wires the stack effect in **both** passes (supported-set check,
  type-check pop REF/pop REF, and `lowerJump`), preserving JVM operand order
  (value2 popped first as `right`, value1 as `left`).
- `IrCppEmitter` emits pointer-identity `left == right` / `left != right` with
  the same `If` + edge-transfer pattern as `ReferenceBranch`. Both-null carries
  as `nullptr == nullptr`, so it correctly takes the `EQ` target.
- `IrMethod.toString` renders `branch if_acmpeq` / `branch if_acmpne`.
- Reject-before-mutation sentinel retargeted from `IF_ACMPEQ` (now admitted)
  to `MONITORENTER` (still rejected); opcode assertion updated. Monitors are
  **not** implemented here.
- Status note: `docs/architecture/ir-if-acmp-status.md`.

Existing `IFNULL`/`IFNONNULL` (`ReferenceBranch`) behavior is unchanged.
Defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`.

Tests:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL`; 114 tests, 0 skipped, 0 failures, 0 errors
(`IrCompilerTest` 107, `CodegenModeTest` 7, from the JUnit XML reports).
No JDK support badge is claimed and no benchmark numbers are invented.

## (b) Ship-ready?

**No.**

## (c) Review required?

**Yes** — Fable-appropriate IR review.

## (d) Review preconditions

- Confirm no defaults changed (`--codegen` stays `legacy`, `--ir-lower` stays
  `direct`, `--backend` stays `cpp`).
- Confirm `ReferenceCompareBranch` compares two references by identity
  (`==` / `!=`), not the null-only `ReferenceBranch` path, and that both-null
  takes the `EQ` target.
- Confirm the stack effect is wired in both the type-check and lowering passes
  (pop REF, pop REF) and that `IFNULL`/`IFNONNULL` are not regressed.
- Confirm the reject-before-mutation sentinel now uses a still-rejected
  construct (`MONITORENTER`) and that this PR does not implement monitors or
  `invokedynamic`.
- Do not mark the IR-admission goal complete.

---

# 接纳 IF_ACMPEQ / IF_ACMPNE 到 typed CFG IR 路径

## (a) 范围

在可选的 typed CFG IR 路径（`--codegen=ir`、`--ir-lower=direct`）上接纳
JVM 的 `IF_ACMPEQ` 与 `IF_ACMPNE`，使按引用同一性分支的方法不再回退到
legacy 字符串拼接生成器。这是继 `LCMP`（#153 / #156）之后的下一个 IR
接纳缺口。

改动：

- 新增终结符 `IrNodes.ReferenceCompareBranch`：两个 `REFERENCE` 操作数
  （`left`、`right`）、`EQ`/`NE` 条件、真/假目标块。它与仅比较 null 的
  `ReferenceBranch`、以及 i32 的 `Branch` 都不同，且不抛异常（同一性比较）。
- `AsmToIr` 在**两个**阶段都接好栈效应（支持集判定、类型检查弹出
  REF/REF、以及 `lowerJump`），并保持 JVM 操作数顺序（先弹 value2 作
  `right`，再弹 value1 作 `left`）。
- `IrCppEmitter` 生成指针同一性比较 `left == right` / `left != right`，
  使用与 `ReferenceBranch` 相同的 `If` + 边转移模式。两个 null 都以
  `nullptr` 承载，因此 `nullptr == nullptr` 会正确走 `EQ` 目标。
- `IrMethod.toString` 打印 `branch if_acmpeq` / `branch if_acmpne`。
- reject-before-mutation 哨兵从 `IF_ACMPEQ`（现已接纳）改为
  `MONITORENTER`（仍被拒绝），并更新操作码断言。本 PR **不**实现监视器。
- 状态说明：`docs/architecture/ir-if-acmp-status.md`。

现有 `IFNULL`/`IFNONNULL`（`ReferenceBranch`）行为不变。默认值不变：
`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。

测试：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL`；114 项测试，0 跳过，0 失败，0 错误
（`IrCompilerTest` 107、`CodegenModeTest` 7，来自 JUnit XML 报告）。
未声称任何 JDK 支持徽章，未编造基准数字。

## (b) 是否可直接上线？

**否。**

## (c) 是否需要审查？

**是** —— 需要 Fable 视角的 IR 审查。

## (d) 审查前置条件

- 确认没有改动默认值（`--codegen` 仍为 `legacy`，`--ir-lower` 仍为
  `direct`，`--backend` 仍为 `cpp`）。
- 确认 `ReferenceCompareBranch` 以同一性（`==` / `!=`）比较两个引用，
  而非仅比较 null 的 `ReferenceBranch`，且两个 null 走 `EQ` 目标。
- 确认在类型检查与降级两个阶段都接好栈效应（弹出 REF、弹出 REF），
  且未回归 `IFNULL`/`IFNONNULL`。
- 确认 reject-before-mutation 哨兵改用仍被拒绝的构造（`MONITORENTER`），
  且本 PR 未实现监视器或 `invokedynamic`。
- 不要把 IR 接纳目标标记为完成。
