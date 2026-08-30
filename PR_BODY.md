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

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：通过。XML：`IrCompilerTest` 387 个测试、`CodegenModeTest` 7 个测试；
共 394 个测试，0 skipped、0 failures、0 errors。

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

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: passed. XML totals: 387 `IrCompilerTest` tests and 7
`CodegenModeTest` tests; 394 total, 0 skipped, 0 failures, 0 errors.

Ship-ready: **No**
