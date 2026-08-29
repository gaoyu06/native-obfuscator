# Admit JVM monitors and synchronized methods on typed CFG IR

## (a) Scope

Admit JVM `MONITORENTER`, `MONITOREXIT`, and synchronized methods on the
opt-in direct typed CFG IR path.

Changes:

- Add typed monitor instructions and pop-one-reference frontend lowering.
- Treat monitor operations as throwing CFG instructions and route failures
  through existing ordered exceptional IR edges.
- Emit JNI `MonitorEnter` / `MonitorExit`, including explicit null-enter NPE
  behavior.
- Prove explicit monitor pairing as a LIFO state across normal and exceptional
  CFG edges before mutation; reject ambiguous or unbalanced methods.
- Enter `this` for synchronized instance methods and the JNI `jclass` for
  synchronized static methods. Release on normal and exceptional returns while
  preserving pending exceptions.
- Clear `ACC_SYNCHRONIZED` only after successful IR validation/lowering so the
  emitted body, rather than both the JVM and body, owns synchronization.
- Retarget `rejectsUnsupportedWideOperationBeforeMutation` to still-unsupported
  `INVOKEDYNAMIC`.
- Add `docs/architecture/ir-monitors-status.md`.

Acceptance command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Exact JUnit XML counts will be recorded after the committed acceptance run.

## (b) Ship-ready?

**No.**

`--codegen` remains `legacy`, and the active IR-complete-method-bodies goal
remains open.

## (c) Acceptance

Acceptance is the committed compile-and-run test gate above, including the
generated C++ harnesses. No stacked code-only review is required.

## (d) Boundaries

- No `INVOKEDYNAMIC`, `ConstantDynamic`, method-handle LDC, `jsr` / `ret`, or
  additional constructor-split implementation.
- No evaluator ISA, interpreter, packaging, CLI-default, README,
  project-status, or current-goal changes.
- Do not flip `--codegen` from `legacy`, delete the snippet path, or mark the
  active goal complete.

---

# 在 typed CFG IR 路径接纳 JVM 监视器与同步方法

## (a) 范围

在可选的 direct typed CFG IR 路径接纳 JVM `MONITORENTER`、
`MONITOREXIT` 与同步方法。

改动：

- 新增 typed monitor 指令，并在前端两个阶段实现弹出一个引用的栈效应。
- 将 monitor 操作视为可抛异常指令，并复用现有有序异常 IR 边。
- 生成 JNI `MonitorEnter` / `MonitorExit`，包括 null enter 的 NPE 行为。
- 在任何变更前，沿普通与异常 CFG 边证明显式 monitor 的 LIFO 配对；
  无法证明或不平衡的方法会被拒绝。
- 同步实例方法进入 `this`，同步静态方法进入 JNI `jclass`；普通及异常
  返回均释放 monitor，异常返回会保留并重新抛出 pending exception。
- 仅在 IR 验证与 lowering 成功后清除 `ACC_SYNCHRONIZED`，避免 JVM 与
  生成体重复持有同步责任。
- reject-before-mutation 哨兵改为仍不支持的 `INVOKEDYNAMIC`。
- 新增 `docs/architecture/ir-monitors-status.md`。

验收命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

提交后的验收运行结束后，将填写 JUnit XML 的精确计数。

## (b) 是否可直接上线？

**否。**

`--codegen` 仍为 `legacy`，IR 完整方法体的当前目标仍未完成。

## (c) 验收

以上已提交代码的编译并执行测试（包括生成的 C++ harness）就是验收门槛；
不要求 stacked code-only review。

## (d) 边界

- 不实现 `INVOKEDYNAMIC`、`ConstantDynamic`、method-handle LDC、
  `jsr` / `ret` 或额外 constructor-split 情况。
- 不修改 evaluator ISA、interpreter、packaging、CLI 默认值、README、
  project-status 或 current-goal。
- 不把 `--codegen` 从 `legacy` 切走，不删除 snippet 路径，也不把当前目标
  标记为完成。
