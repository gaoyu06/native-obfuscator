## English

### Summary

- Keep cross-suffix and chain-covering constructor exception tables rejected.
- Prove both untouched Java 8 bytecode shapes load and execute on both
  constructor paths.
- Verify rejection preserves instruction identity, try/catch labels, generated
  source, hidden methods, and the singular `MethodContext.proxyMethod`.
- Update only the IR flexible-constructor verification bullets.

### Admission

No. This change adds fail-closed coverage and does not broaden constructor
admission.

### Verification

The child focused gate will be recorded after the initial implementation
commit is pushed. The parent will re-run the full suite and report its
authoritative totals.

Ship-ready: **No**

## 中文

### 摘要

- 继续拒绝跨后缀以及保护范围覆盖构造器链调用的异常表。
- 验证两种未经过 IR 重写的 Java 8 字节码均可加载，并可执行构造器的两条路径。
- 验证拒绝后指令对象身份、try/catch 标签、生成源码、隐藏方法以及单一的
  `MethodContext.proxyMethod` 均保持不变。
- 仅更新 IR 灵活构造器文档中的验证条目。

### 准入

否。本变更只增加 fail-closed 覆盖，不扩大构造器准入范围。

### 验证

子任务会在首次实现提交推送后记录聚焦测试结果。父任务将重新运行完整测试套件，
并报告其权威汇总。

可发布：**否**
