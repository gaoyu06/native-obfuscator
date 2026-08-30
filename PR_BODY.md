## English

### Scope

- Admit retained-prefix `LALOAD`, `FALOAD`, and `DALOAD` constructor leaves
  shaped exactly as `ALOAD <declared [J/[F/[D argument>; ILOAD <index>;
  xALOAD`.
- The index `ILOAD` must be one instruction and must load either a declared
  int constructor argument or a proven prefix extra-local copy created by one
  dominating `ILOAD declared; ISTORE extra`.
- The array source remains an unchanged, directly loaded declared argument of
  the exact matching type. The load stays in JVM bytecode, preserving JVM null,
  bounds, and wide-value behavior.
- Keep one hidden native proxy method per rewritten constructor.

### Still rejected

- Extra-local wide-array sources, including an extra-array source combined with
  an extra-local index.
- Computed or `INEG` indexes, computed extra-index stores, overwritten or
  otherwise unproven extra indexes, mismatched arrays, and prior array stores.
- Skip-super shapes and any change to the `legacy` `--codegen` default.

### Verification

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest` passed.
- Observed XML: `IrCompilerTest` 445 tests and `CodegenModeTest` 7 tests;
  0 skipped, 0 failures, and 0 errors in both suites.
- Runtime coverage:
  `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity`.

Ship-ready: **No**

## 中文

### 范围

- 放行严格形如 `ALOAD <已声明的 [J/[F/[D 参数>; ILOAD <索引>;
  xALOAD` 的构造器保留前缀 `LALOAD`、`FALOAD` 和 `DALOAD` 叶子。
- 索引必须只有一条 `ILOAD` 指令，并且来源只能是已声明的 `int`
  构造器参数，或由唯一且支配所有调用的
  `ILOAD declared; ISTORE extra` 建立的已证明前缀额外局部变量副本。
- 数组源仍必须是未修改、直接加载、类型完全匹配的已声明参数。
  数组加载继续留在 JVM 字节码中，以保留 JVM 的空指针、越界及宽值语义。
- 每个改写后的构造器仍只生成一个隐藏 native 代理方法。

### 仍然拒绝

- 额外局部变量中的宽数组源，包括“额外数组源 + 额外索引”的组合。
- 计算索引、`INEG` 索引、通过计算写入的额外索引、被覆盖或未证明的额外
  索引、数组类型不匹配，以及之前存在数组写入的情况。
- 跳过 super 的形状，以及任何对 `legacy` `--codegen` 默认值的修改。

### 验证

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest` 已通过。
- XML 实测结果：`IrCompilerTest` 445 项，`CodegenModeTest` 7 项；
  两个套件均为 0 跳过、0 失败、0 错误。
- 运行时覆盖：
  `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity`。

可发布：**否（No）**
