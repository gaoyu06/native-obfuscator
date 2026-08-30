## English

### Summary

- Keep every audited constructor path that can skip all `this`/`super` calls
  rejected before IR mutation; no production admission rule changes.
- Preserve constructor instruction identity, generated source, hidden-method
  inventory, and the singular `MethodContext.proxyMethod` on rejection.
- Make the skip-super fixtures verifier-valid with an explicit pre-super throw,
  and execute the untouched Java 8 branch, switch, diamond, immediate-return,
  and distinct-suffix classes on normal and skip-super paths.
- Update only the flexible-constructor status verification bullets.

### Admission

No. Skip-super remains fail-closed.

### Rebase

Rebased onto `origin/master` `92bed90` (post-#268 isolated no-arg NEW).
Parent-verified JUnit XML after rebase: `IrCompilerTest` 471/471 and
`CodegenModeTest` 7/7, with zero failures, errors, or skips (total 478). The
verification test is `skipSuperConstructorShapesPassJava8JvmVerification`.

The CLI default remains `legacy`.

Ship-ready: **No**

## 中文

### 摘要

- 所有可能跳过全部 `this`/`super` 调用的已审计构造器路径继续在 IR
  修改前拒绝；不修改生产准入规则。
- 拒绝后构造器指令对象身份、生成源码、隐藏方法清单以及单一
  `MethodContext.proxyMethod` 均保持不变。
- 通过显式的 super 调用前抛异常使 skip-super 样例可通过验证器，并在
  Java 8 下执行未重写的分支、开关、菱形、立即返回和不同后缀类的普通
  路径与 skip-super 路径。
- 仅更新灵活构造器状态文档的验证条目。

### 准入

否。skip-super 继续 fail-closed。

### 变基

已变基到 `origin/master` `92bed90`（#268 无参 NEW 之后）。父代理变基后
聚焦 JUnit XML：`IrCompilerTest` 471/471、`CodegenModeTest` 7/7，失败、错误
和跳过均为 0（合计 478）。验证测试为
`skipSuperConstructorShapesPassJava8JvmVerification`。

命令行默认代码生成模式仍为 `legacy`。

可发布：**否**
