## English

### Summary
- Adds fixture-only IR admission coverage for a five-argument `GregorianCalendar` initializer whose third and fifth arguments come from the extra int local.
- Adds admission, rewritten-bytecode verification, and Java/native parity tests.
- Leaves `ConstructorSpecialMethodProcessor` and all production compiler/runtime code unchanged.

### Verification
- The three new `IrCompilerTest` methods pass locally.
- `IrCompilerTest` contains 658 `@Test` annotations and 658 public test methods.
- Expected parent XML total after the seven sibling branches land: 665.
- Ship-ready: No.

## 中文

### 摘要
- 为五参数 `GregorianCalendar` 初始化器新增仅测试夹具的 IR 准入覆盖，其中第三和第五个参数来自额外的 int 局部变量。
- 新增准入、重写后字节码校验，以及 Java/原生执行一致性测试。
- `ConstructorSpecialMethodProcessor` 和所有生产编译器/运行时代码均未修改。

### 验证
- 三个新增的 `IrCompilerTest` 方法均在本地通过。
- `IrCompilerTest` 包含 658 个 `@Test` 注解和 658 个 public 测试方法。
- 七个同级分支合入后，父分支 XML 预期总数为 665。
- 可发布：否。
