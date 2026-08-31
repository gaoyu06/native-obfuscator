# test: admit extra-local int as the first and fourth six-arg NEW arguments

## English

### Baseline and scope

- Baseline: leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432) at `ce634292263daedd23c3086bb66784730350c54b` (`ce634292`).
- Adds fixture/test wiring for `new-constructor-extra-local-argument-six-first-fourth` in `IrCompilerTest`.
- Processor changed: No. Production compiler/runtime code is unchanged.

### Coverage

- The retained JVM prefix contains `NEW GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; BIPUSH 6; INVOKESPECIAL <init>(IIIIII)V`.
- The native `(II)V` body contains only `RETURN`; the retained constructor has three direct chain calls, one hidden bridge, and proxy descriptor `(Ljava/lang/Object;II)V` through the singular `MethodContext.proxyMethod`.
- Adds admission, rewritten JVM verification, and Java/native parity tests.

### Wiring

- First argument: remains `ILOAD 3`; the shape is not on the first-argument exclude list.
- Second and third arguments: remain `ICONST_2` and `ICONST_3`.
- Fourth argument: added to the `ILOAD 3` list.
- Fifth and sixth arguments: remain `ICONST_5` and `BIPUSH 6`.
- Constructor argument count: 6. Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

### Validation and status

- Latest compiler parent XML until the parent reruns: [#432](https://github.com/gaoyu06/native-obfuscator/pull/432), 737 tests (`IrCompilerTest` 730 + `CodegenModeTest` 7).
- Expected parent XML after this leftover-docs child: 740 tests (737 + 3).
- Child XML: 740 tests (`IrCompilerTest` 733 + `CodegenModeTest` 7), with 0 skipped, 0 failures, and 0 errors. The parent will discard this child-only total.
- Local verification passed: `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`.
- Defaults unchanged: IR code generation/lowering/backend defaults were not flipped.
- Ship-ready: No.
- PR opened: No.

## 中文

### 基线与范围

- 基线：leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432)，提交 `ce634292263daedd23c3086bb66784730350c54b`（`ce634292`）。
- 在 `IrCompilerTest` 中增加 `new-constructor-extra-local-argument-six-first-fourth` 的夹具和测试接线。
- 处理器修改：否。生产编译器和运行时代码均未修改。

### 覆盖

- 保留的 JVM 前缀包含 `NEW GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; BIPUSH 6; INVOKESPECIAL <init>(IIIIII)V`。
- 原生 `(II)V` 方法体仅包含 `RETURN`；保留的构造器包含三次直接链调用、一个隐藏桥，且通过单数的 `MethodContext.proxyMethod` 使用代理描述符 `(Ljava/lang/Object;II)V`。
- 新增准入、重写后 JVM 验证和 Java/原生一致性测试。

### 接线

- 第一参数：保持 `ILOAD 3`；该形状不加入第一参数排除列表。
- 第二、第三参数：分别保持 `ICONST_2`、`ICONST_3`。
- 第四参数：加入 `ILOAD 3` 列表。
- 第五、第六参数：分别保持 `ICONST_5`、`BIPUSH 6`。
- 构造器参数数目：6。链描述符：`(Ljava/util/GregorianCalendar;)V`。

### 验证与状态

- 父分支重新运行前的最新编译器 XML：[＃432](https://github.com/gaoyu06/native-obfuscator/pull/432)，共 737 项（`IrCompilerTest` 730 + `CodegenModeTest` 7）。
- 此 leftover-docs 子项合入后的预期父 XML：740 项（737 + 3）。
- 子分支 XML：共 740 项（`IrCompilerTest` 733 + `CodegenModeTest` 7），0 项跳过、0 项失败、0 项错误。父分支将丢弃此子分支专用总数。
- 本地验证通过：`CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`。
- 默认值未改变：未切换 IR 代码生成、lowering 或后端默认值。
- 可发布：否。
- 已打开 PR：否。
