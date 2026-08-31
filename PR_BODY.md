# test: admit extra-local int as the second six-arg NEW argument

## English

This fixture-only change admits the shape
`new-constructor-extra-local-argument-six-second`.

- Helper wiring: first initializer argument `ICONST_1`; second `ILOAD 3`;
  third/fourth/fifth `ICONST_3` / `ICONST_4` / `ICONST_5`; sixth
  `BIPUSH 6`.
- Constructor argument count: 6.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.
- Processor changed: **No**
- Product defaults (`--codegen`, `--ir-lower`, and `--backend`) unchanged
- Rebased onto leftover-docs #416 (`c0cff1a001daf40f64d7279967f7677337185a42`).
- Parent re-ran **716/716** (`IrCompilerTest` 709 + `CodegenModeTest` 7), including `threeImmediateNewExtraLocalSixSecondArgChainInputsCompileAndRunWithJavaParity`. Zero failures/errors/skips.
- Ship-ready: **No**
- This is fixture admission coverage, not a JDK support badge.

## 中文

本次仅修改测试夹具，新增接纳形状
`new-constructor-extra-local-argument-six-second`。

- 辅助方法接线：初始化器第一个参数为 `ICONST_1`，第二个参数为
  `ILOAD 3`，第三/第四/第五个参数分别为 `ICONST_3` / `ICONST_4` /
  `ICONST_5`，第六个参数为 `BIPUSH 6`。
- 构造器参数数量：6。
- 链式调用描述符：`(Ljava/util/GregorianCalendar;)V`。
- 是否修改处理器：**否**
- 产品默认值（`--codegen`、`--ir-lower` 和 `--backend`）保持不变
- 已 rebase 到 leftover-docs #416（`c0cff1a001daf40f64d7279967f7677337185a42`）。
- 父级重跑 **716/716**（`IrCompilerTest` 709 + `CodegenModeTest` 7），含 `threeImmediateNewExtraLocalSixSecondArgChainInputsCompileAndRunWithJavaParity`。失败/错误/跳过均为零。
- 可发布状态：**否**
- 此项仅为测试夹具准入覆盖，不代表 JDK 支持徽章。
