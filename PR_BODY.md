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

Child-only JUnit XML:

- `IrCompilerTest`: 465 tests, 0 failures, 0 errors, 0 skipped
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped
- Total: 472 tests, 0 failures, 0 errors, 0 skipped
- Source count: 465 exact `@Test` lines and 465 `public void` tests
- New JVM verification:
  `skipSuperConstructorShapesPassJava8JvmVerification`

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

子代理专属聚焦门禁使用上方命令。子代理专属 JUnit XML：

- `IrCompilerTest`：465，失败/错误/跳过均为 0
- `CodegenModeTest`：7，失败/错误/跳过均为 0
- 合计：472，失败/错误/跳过均为 0
- 源码计数：465 个严格匹配的 `@Test` 行，465 个 `public void` 测试
- 新增 JVM 验证：`skipSuperConstructorShapesPassJava8JvmVerification`

可发布：**否**
<!-- CURSOR_AGENT_PR_BODY_END -->
