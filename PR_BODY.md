<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
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

### Verification

Child-only focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals will be recorded after the gate.

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

### 验证

子代理专属聚焦门禁使用上方命令。门禁完成后记录 JUnit XML 汇总。

可发布：**否**
<!-- CURSOR_AGENT_PR_BODY_END -->
