# test: admit extra-local int as the second and sixth six-arg NEW arguments

## English

This fixture-only change admits the shape
`new-constructor-extra-local-argument-six-second-sixth`.

Wiring checklist:
- First argument exclude: yes (`ICONST_1` remains the first initializer argument)
- Second argument `ILOAD 3`: yes
- Third argument `ILOAD 3`: no (`ICONST_3`)
- Fourth argument `ILOAD 3`: no (`ICONST_4`)
- Fifth argument `ILOAD 3`: no (`ICONST_5`)
- Sixth argument `ILOAD 3`: yes
- Initializer argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

Rebased onto leftover-docs #445 at `610945ba`
(`610945bac1be76d021f8c3f4918bff1cb2df829c`).

Parent XML: **758** (`IrCompilerTest` 751 + `CodegenModeTest` 7), 0
failures/errors/skipped.

Parent re-ran `threeImmediateNewExtraLocalSixSecondSixthArgChainInputsCompileAndRunWithJavaParity`.

- Processor changed: No
- Defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, and
  `--backend=cpp` remain unchanged
- Ship-ready: No
- Production goal incomplete

## 中文

此纯测试夹具变更准入形状
`new-constructor-extra-local-argument-six-second-sixth`。

接线检查清单：
- 第一参数排除：是（首个初始化参数保持为 `ICONST_1`）
- 第二参数 `ILOAD 3`：是
- 第三参数 `ILOAD 3`：否（`ICONST_3`）
- 第四参数 `ILOAD 3`：否（`ICONST_4`）
- 第五参数 `ILOAD 3`：否（`ICONST_5`）
- 第六参数 `ILOAD 3`：是
- 初始化参数数量：`6`
- 链描述符：`(Ljava/util/GregorianCalendar;)V`

已变基到 leftover-docs #445，`610945ba`
（`610945bac1be76d021f8c3f4918bff1cb2df829c`）。

父级 XML：**758**（`IrCompilerTest` 751 + `CodegenModeTest` 7），失败/错误/跳过均为 0。

父级复跑包含 `threeImmediateNewExtraLocalSixSecondSixthArgChainInputsCompileAndRunWithJavaParity`。

- Processor changed: No（处理器未更改）
- 默认值未更改：`--codegen=legacy`、`--ir-lower=direct` 和
  `--backend=cpp` 均保持不变
- Ship-ready: No（尚不可发布）
- 生产目标尚未完成
