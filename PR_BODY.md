## English

### (a) What changed?

- Admit suffix-protected constructor try/catch regions whose isolated prefix
  handler is exactly `ASTORE n; RETURN` or `ASTORE n; GOTO ret`, with `ret`
  resolving to an isolated prefix `RETURN`.
- Require `n` to be a non-receiver reference slot and reject category-2 holes,
  extra handler work, stored-exception uses, invalid return targets, and extra
  incoming return-block edges before mutation.
- Relocate the admitted handler into the IR suffix while omitting its dead
  prefix block from the bytecode wrapper. No hidden-bridge argument is added.
- Add focused admission, rejection, JVM verification, and JNI parity coverage.

### (b) Ship-ready?

No.

### (c) Validation

Required focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: 205 tests passed (198 + 7), with 0 failures, 0 errors, and
0 skipped.

### (d) Scope and defaults

This is a fail-closed extension for two exact constructor handler sequences.
Other mixed try/catch placements and handler shapes remain rejected. The
`--codegen legacy`, `--ir-lower direct`, and `--backend cpp` defaults are
unchanged.

## 中文

### (a) 改动内容

- 支持后缀受保护区间对应的两种精确构造函数前缀处理器：
  `ASTORE n; RETURN` 和 `ASTORE n; GOTO ret`，其中 `ret` 必须指向隔离的前缀
  `RETURN`。
- 要求 `n` 是非接收者引用槽位；在任何改写前拒绝 category-2 空洞、额外处理器
  指令、已存异常的后续使用、非返回目标以及返回块的额外入边。
- 将已验证的处理器迁移到 IR 后缀，并从字节码包装器中移除对应的死前缀块；
  不新增隐藏桥接参数。
- 新增准入、拒绝、JVM 验证和 JNI 运行一致性测试。

### (b) 可直接上线？

否。

### (c) 验证

必跑的聚焦测试：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：205 项测试全部通过（198 + 7），0 项失败、0 项错误、0 项跳过。

### (d) 范围与默认值

本改动仅以 fail-closed 方式扩展两个精确构造函数处理器序列。其他混合
try/catch 标签位置和处理器形状仍保持拒绝。`--codegen legacy`、
`--ir-lower direct` 和 `--backend cpp` 默认值均未改变。
