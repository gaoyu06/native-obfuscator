# test: admit extra-local int as first, third, fourth, and fifth five-arg NEW arguments

## English

This fixture-only change admits the shape
`new-constructor-extra-local-argument-five-first-third-fourth-fifth`.

- Helper wiring: first `ILOAD 3`, second `ICONST_2`, third/fourth/fifth
  `ILOAD 3`
- Constructor argument count: 5
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`
- Processor changed: No
- Product defaults (`--codegen`, `--ir-lower`, and `--backend`) unchanged
- Rebased onto leftover-docs #412 (`54793a8c`).
- Parent re-ran **710/710** (`IrCompilerTest` 703 + `CodegenModeTest` 7), including `threeImmediateNewExtraLocalFiveFirstThirdFourthFifthArgChainInputsCompileAndRunWithJavaParity`. Zero failures/errors/skips.
- Ship-ready: No
- This is fixture admission coverage, not a JDK support badge.

## 中文

这个仅修改测试夹具的变更接纳以下形状：
`new-constructor-extra-local-argument-five-first-third-fourth-fifth`。

- 辅助方法接线：第一个参数为 `ILOAD 3`，第二个参数为 `ICONST_2`，
  第三、第四、第五个参数为 `ILOAD 3`
- 构造器参数数量：5
- 链描述符：`(Ljava/util/GregorianCalendar;)V`
- 是否修改处理器：否
- 产品默认值（`--codegen`、`--ir-lower` 和 `--backend`）保持不变
- 已 rebase 到 leftover-docs #412（`54793a8c`）。
- 父级重跑 **710/710**（`IrCompilerTest` 703 + `CodegenModeTest` 7），含 `threeImmediateNewExtraLocalFiveFirstThirdFourthFifthArgChainInputsCompileAndRunWithJavaParity`。失败/错误/跳过均为零。
- 可发布状态：否
- 这是测试夹具接纳覆盖，不代表 JDK 支持标记。
