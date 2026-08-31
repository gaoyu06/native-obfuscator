# test: admit extra-local int as the first and third six-arg NEW arguments

## English

### Summary

- Adds fixture-only IR compiler coverage for an extra-local `int` used as the
  first and third arguments of a six-argument `GregorianCalendar` bytecode
  initializer.
- Keeps `NEW; DUP; args; <init>` in the retained JVM prefix. The native
  `(II)V` body contains only `RETURN`; the constructor uses one hidden bridge
  and a `(Ljava/lang/Object;II)V` proxy.
- Leaves the processor and production compiler/runtime code unchanged.

### Fixture wiring

- Shape: `new-constructor-extra-local-argument-six-first-third`
- First-argument exclude list: unchanged (not excluded; emits `ILOAD 3`)
- Second-argument `ILOAD 3` list: unchanged (emits `ICONST_2`)
- Third-argument `ILOAD 3` list: added (emits `ILOAD 3`)
- Fourth/fifth/sixth `ILOAD 3` lists: unchanged (emit `ICONST_4`,
  `ICONST_5`, and `BIPUSH 6`)
- Constructor argument count: 6
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalSixFirstThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixFirstThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixFirstThirdArgChainInputsCompileAndRunWithJavaParity`

### Baseline and validation

- Leftover-docs baseline: [#430](https://github.com/gaoyu06/native-obfuscator/pull/430)
  at [`46a53713`](https://github.com/gaoyu06/native-obfuscator/commit/46a537135b309b2002e7610d6e38407f3ac26f82).
- Latest compiler parent XML until the parent re-runs: **#430 (734)**
  (`IrCompilerTest` 727 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: **737** (734 + 3).
- Child XML: **737** (`IrCompilerTest` 730 + `CodegenModeTest` 7), with
  0 failures, 0 errors, and 0 skipped.
- Processor changed: **No**.
- Ship-ready: **No**.
- Defaults unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or
  `--backend=cpp` override.

## 中文

### 摘要

- 仅增加 IR 编译器测试夹具覆盖：将额外局部 `int` 用作六参数
  `GregorianCalendar` 字节码初始化器的第一和第三个参数。
- 完整的 `NEW; DUP; args; <init>` 保留在 JVM 前缀中。原生 `(II)V`
  方法体仅包含 `RETURN`；构造器使用一个隐藏桥接和
  `(Ljava/lang/Object;II)V` 代理。
- 处理器及生产编译器/运行时代码均未修改。

### 夹具接线

- 形状：`new-constructor-extra-local-argument-six-first-third`
- 第一参数排除列表：不变（未排除，发出 `ILOAD 3`）
- 第二参数 `ILOAD 3` 列表：不变（发出 `ICONST_2`）
- 第三参数 `ILOAD 3` 列表：已添加（发出 `ILOAD 3`）
- 第四/第五/第六参数 `ILOAD 3` 列表：不变（分别发出 `ICONST_4`、
  `ICONST_5` 和 `BIPUSH 6`）
- 构造器参数数量：6
- 链描述符：`(Ljava/util/GregorianCalendar;)V`

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalSixFirstThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixFirstThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixFirstThirdArgChainInputsCompileAndRunWithJavaParity`

### 基线与验证

- leftover-docs 基线：[#430](https://github.com/gaoyu06/native-obfuscator/pull/430)，
  提交为 [`46a53713`](https://github.com/gaoyu06/native-obfuscator/commit/46a537135b309b2002e7610d6e38407f3ac26f82)。
- 父任务重新运行前的最新编译器 XML：**#430（734）**
  （`IrCompilerTest` 727 + `CodegenModeTest` 7）。
- leftover-docs 之后预期的父任务 XML：**737**（734 + 3）。
- 子任务 XML：**737**（`IrCompilerTest` 730 + `CodegenModeTest` 7），
  0 个失败、0 个错误、0 个跳过。
- 处理器修改：**否**。
- 可发布：**否**。
- 默认选项不变：未使用 `--codegen=legacy`、`--ir-lower=direct` 或
  `--backend=cpp` 覆盖。
