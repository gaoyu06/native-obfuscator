## English

### (a) Scope

Blinded reader notes for the live-mix `published.so`, including per-method
native-code reconstruction and post-recovery scoring.

### (b) Ship-ready?

No. This is an N=1 evaluation artifact, not a production change.

### (c) Review required?

Yes — confirm the blinding order: `recovery.md` was committed before
`published.jar`, `run.md`, or the fixture source was opened.

### (d) Preconditions

Do not cite this result as a bar pass/fail without checking the committed
recovery against the fixture source.

## 中文

### (a) 范围

针对 live-mix `published.so` 的盲读记录，包括逐方法的原生代码恢复，以及在恢复结果提交后的评分。

### (b) 可以直接发布吗？

不可以。这是一个 N=1 的评估产物，不是生产代码变更。

### (c) 需要审查吗？

需要——请确认盲测顺序：先提交 `recovery.md`，之后才打开
`published.jar`、`run.md` 或测试夹具源码。

### (d) 前置条件

在将本结果引用为门槛通过/失败的依据之前，必须先核对已提交的恢复结果与测试夹具源码。
