# test: admit extra-local int as third, fourth, and fifth five-arg NEW arguments

## English

### Summary
- Fixture-only IR admission; `ConstructorSpecialMethodProcessor` is unchanged.
- Adds the five-argument `GregorianCalendar` leaf `ICONST_1; ICONST_2; ILOAD 3; ILOAD 3; ILOAD 3`.
- Covers IR admission, rewritten JVM verification, and native Java parity.
- Runtime verification: `threeImmediateNewExtraLocalFiveThirdFourthFifthArgChainInputsCompileAndRunWithJavaParity`.
- Parent XML totals are pending the parent's re-run.

### Verification
- Required Gradle selection passed.
- Child-local XML: 698 tests (`IrCompilerTest` 691 + `CodegenModeTest` 7),
  with 0 failures, 0 errors, and 0 skipped.

### Release status
- Ship-ready: No.
- This does not switch `--codegen` away from `legacy`.

## 中文

### 摘要
- 仅扩展 IR 测试夹具准入；`ConstructorSpecialMethodProcessor` 未修改。
- 新增五参数 `GregorianCalendar` 叶节点：`ICONST_1; ICONST_2; ILOAD 3; ILOAD 3; ILOAD 3`。
- 覆盖 IR 准入、重写后的 JVM 校验及原生执行与 Java 一致性。
- 运行时校验：`threeImmediateNewExtraLocalFiveThirdFourthFifthArgChainInputsCompileAndRunWithJavaParity`。
- 父分支 XML 总数等待父代理重新运行后确认。

### 验证
- 要求的 Gradle 测试组合已通过。
- 子分支本地 XML：698 项测试（`IrCompilerTest` 691 +
  `CodegenModeTest` 7），0 失败、0 错误、0 跳过。

### 发布状态
- 可发布：否。
- 本变更不会将 `--codegen` 从 `legacy` 切换出去。
