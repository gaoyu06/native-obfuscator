# test: admit extra-local int as the fourth and sixth six-arg NEW arguments

## English

### Summary

- Add the fixture shape `new-constructor-extra-local-argument-six-fourth-sixth`.
- Admit a proven extra-local `int` copy as the fourth and sixth arguments of a six-argument `GregorianCalendar` `NEW`.
- Keep the constructor descriptor `(II)V`, prefix `ILOAD 2; ISTORE 3`, `maxLocals = 4`, native body `(II)V` with only `RETURN`, proxy descriptor `(Ljava/lang/Object;II)V`, one hidden bridge, and one native method per constructor.
- Rebased onto leftover-docs [#454](https://github.com/gaoyu06/native-obfuscator/pull/454) at `a44e3237` (`a44e32370cafd2495dec92bd2c29f3763a5c8471`).

### Fixture wiring

- First-argument exclude: **Yes**
- Second argument uses `ILOAD 3`: **No**
- Third argument uses `ILOAD 3`: **No**
- Fourth argument uses `ILOAD 3`: **Yes**
- Fifth argument uses `ILOAD 3`: **No**
- Sixth argument uses `ILOAD 3`: **Yes**
- Argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Tests and status

- Add admission, JVM verification, and Java/native parity tests.
- Parent XML: **773** (`IrCompilerTest` 766 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixFourthSixthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: **No**
- Ship-ready: **No**
- The production goal remains incomplete.
- Defaults are unchanged.

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-fourth-sixth`。
- 允许已证明的额外局部 `int` 副本作为六参数 `GregorianCalendar` `NEW` 的第四和第六个初始化参数。
- 构造函数描述符保持 `(II)V`，前缀保持 `ILOAD 2; ISTORE 3`，`maxLocals = 4`；原生方法体保持 `(II)V` 且仅含 `RETURN`；代理描述符保持 `(Ljava/lang/Object;II)V`；每个构造函数仅有一个隐藏桥接和一个原生方法。
- 已变基到 leftover-docs [#454](https://github.com/gaoyu06/native-obfuscator/pull/454)，`a44e3237`（`a44e32370cafd2495dec92bd2c29f3763a5c8471`）。

### 夹具接线

- 首参数排除列表：**是**
- 第二参数使用 `ILOAD 3`：**否**
- 第三参数使用 `ILOAD 3`：**否**
- 第四参数使用 `ILOAD 3`：**是**
- 第五参数使用 `ILOAD 3`：**否**
- 第六参数使用 `ILOAD 3`：**是**
- 参数数量：`6`
- 链式描述符：`(Ljava/util/GregorianCalendar;)V`

### 测试与状态

- 新增准入、JVM 验证和 Java/原生一致性测试。
- 父级 XML：**773**（`IrCompilerTest` 766 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixFourthSixthArgChainInputsCompileAndRunWithJavaParity`。
- 处理器已修改：**否**
- 可直接发布：**否**
- 生产目标仍未完成。
- 默认选项未改变。
