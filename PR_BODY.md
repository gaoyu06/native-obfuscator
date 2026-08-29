## English

### (a) What changed?

- Extends the flexible constructor split from exactly two distinct linear suffixes to two through eight reachable this/super paths whose nonempty `RETURN`-terminated suffixes are pairwise instruction-distinct.
- Keeps one hidden bridge and appends path ids at every retained chain-call path. Two paths retain the existing `IFNE` dispatch; three or more use an exact-range `TABLESWITCH` with an explicit throwing default.
- Preserves fail-closed handling for partly identical suffix sets, control flow in suffixes, exception tables, extra-local suffix access, unsafe receiver state, and unproven chain inputs.
- Adds three-path JVM verification and CMake/g++ JNI parity coverage, plus positive four-path coverage and updated negative fixtures.

### (b) Ship-ready?

No.

### (c) Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: 210 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 217 tests, 0 failures, 0 errors, 0 skipped.

### (d) Risks and follow-ups

- Admission is intentionally capped at eight paths.
- Suffix sets containing any instruction-identical pair remain rejected; no hybrid join-plus-path-id rewrite is attempted.
- The two-path native body retains its existing `IFNE` dispatch, while larger admitted sets use `TABLESWITCH`.
- Compiler defaults remain unchanged (`--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`).

## 中文

### (a) 改动内容

- 将灵活构造函数拆分从“恰好两个不同的线性后缀”扩展为二至八条可达的 this/super 路径；每个后缀均非空、以 `RETURN` 结束，且任意两个后缀的指令都不完全相同。
- 仍只创建一个隐藏桥接方法，并在每条保留的构造函数链调用路径后附加路径编号。两条路径继续使用原有 `IFNE` 分派；三条及以上路径使用覆盖精确范围的 `TABLESWITCH`，默认分支显式抛出异常。
- 对部分相同的后缀集合、后缀内控制流、异常表、后缀额外局部变量访问、不安全的接收者状态及未经证明的链调用输入继续采用失败关闭策略。
- 新增三路径 JVM 校验与 CMake/g++ JNI 一致性测试，并增加四路径正向测试及更新后的负向用例。

### (b) 可直接上线？

否。

### (c) 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`：210 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 合计：217 个测试，0 个失败，0 个错误，0 个跳过。

### (d) 风险与后续

- 当前准入路径数有意限制为最多八条。
- 只要后缀集合中存在任意一对指令完全相同的后缀，就继续拒绝；不会组合共享汇合点与路径编号两种重写。
- 两路径原生方法继续使用原有 `IFNE` 分派，更大的已准入集合使用 `TABLESWITCH`。
- 编译器默认值保持不变（`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`）。
