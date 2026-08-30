## 中文

### 变更

- 为构造器多分支前缀新增严格的引用链输入证明：
  `ALOAD` 未改写的已声明数组参数、整型常量下标、`AALOAD`。
- `AALOAD` 与三个 `INVOKESPECIAL <init>` 保留在 JVM 构造器前缀；
  共享的 `RETURN` 仍由单个隐藏 native bridge 处理。
- 未证明的数组来源或下标、非数组声明类型、先前数组写入及其他引用计算
  继续在任何 bridge/C++ 变更前拒绝。
- 增加准入、JVM 验证、拒绝前不变性及 Java/native 运行时一致性测试。

### 验证

最终验证命令与 XML 计数将在测试完成后补充。

可发布 / Ship-ready: **No**

## English

### Changes

- Adds a fail-closed reference chain-input proof for constructor multi-call
  prefixes: `ALOAD` of an unchanged declared array argument, an int constant
  index, and `AALOAD`.
- Keeps `AALOAD` and all three `INVOKESPECIAL <init>` calls in the JVM prefix;
  the shared `RETURN` remains behind one hidden native bridge.
- Continues to reject unproven array sources or indexes, non-array declared
  types, prior array stores, and all other unproven reference computations
  before bridge or C++ mutation.
- Adds admission, rewritten JVM verification, reject-before-mutation, and
  Java/native runtime parity coverage.

### Verification

The final verification command and XML totals will be added after testing.

Ship-ready: **No**
