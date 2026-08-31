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

Baseline: leftover-docs #444 at `c89015d2`
(`c89015d2b35ec7bf9511ad0301a5434d2e05a818`).

Latest compiler parent XML until the parent re-runs: **#444 (755)**
(`IrCompilerTest` 748 + `CodegenModeTest` 7).

Expected parent XML after leftover-docs: **758**
(`IrCompilerTest` 751 + `CodegenModeTest` 7).

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

基线：leftover-docs #444，提交 `c89015d2`
（`c89015d2b35ec7bf9511ad0301a5434d2e05a818`）。

父分支重新运行前的最新编译器 XML：**#444（755）**
（`IrCompilerTest` 748 + `CodegenModeTest` 7）。

leftover-docs 之后预期的父分支 XML：**758**
（`IrCompilerTest` 751 + `CodegenModeTest` 7）。

- Processor changed: No（处理器未更改）
- 默认值未更改：`--codegen=legacy`、`--ir-lower=direct` 和
  `--backend=cpp` 均保持不变
- Ship-ready: No（尚不可发布）
- 生产目标尚未完成
