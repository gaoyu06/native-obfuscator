# test: admit extra-local int as second, third, and fifth five-arg NEW arguments

## English

- Fixture-only IR admission coverage; `ConstructorSpecialMethodProcessor` is unchanged.
- The retained JVM-prefix leaf is `ICONST_1; ILOAD 3; ILOAD 3; ICONST_4; ILOAD 3` before `GregorianCalendar.<init>(IIIII)V`.
- Runtime verification:
  `threeImmediateNewExtraLocalFiveSecondThirdFifthArgChainInputsCompileAndRunWithJavaParity`.
- Parent XML totals are pending the parent re-run.
- Ship-ready: No. This does not switch `--codegen` away from `legacy`.

## 中文

- 仅新增 IR 准入测试夹具；`ConstructorSpecialMethodProcessor` 未修改。
- 保留在 JVM 前缀中的叶子序列为
  `ICONST_1; ILOAD 3; ILOAD 3; ICONST_4; ILOAD 3`，随后调用
  `GregorianCalendar.<init>(IIIII)V`。
- 运行时验证：
  `threeImmediateNewExtraLocalFiveSecondThirdFifthArgChainInputsCompileAndRunWithJavaParity`。
- 父分支 XML 总数等待父任务重新运行后确认。
- 可发布：否。本变更不会把 `--codegen` 从 `legacy` 切走。
