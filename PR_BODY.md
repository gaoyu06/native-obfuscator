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

- Extra-array plus extra-index together, computed or `INEG` indexes,
  computed extra-index stores, overwritten or otherwise unproven extra
  indexes, mismatched arrays, and prior array stores.
- Skip-super shapes and any change to the `legacy` `--codegen` default.

### Rebase

- Rebased onto `origin/master` `209761b` (post-#258 extra-local wide-array
  sources). Child XML on the pre-#258 merge-base is stale and is not the
  parent number. Extra-local constant-index wide sources stay admitted.

### Verification

- Parent will re-run the full focused `IrCompilerTest` + `CodegenModeTest`
  suite on this rebased branch and report JUnit XML from
  `obfuscator/build/test-results/test/`.
- Runtime name to confirm:
  `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity()`.

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

- 额外数组源与额外索引的组合、计算索引、`INEG` 索引、通过计算写入的额外
  索引、被覆盖或未证明的额外索引、数组类型不匹配，以及之前存在数组写入。
- 跳过 super 的形状，以及任何对 `legacy` `--codegen` 默认值的修改。

### 变基

- 已变基到 `origin/master` `209761b`（#258 extra-local 宽数组源之后）。
  变基前的子代理 XML 不是父代理数字。常量索引的 extra-local 宽数组源
  仍按 #258 接纳。

### 验证

- 父代理将在变基后的分支上重跑完整聚焦套件，并从
  `obfuscator/build/test-results/test/` 读取 JUnit XML。
- 运行时名称：
  `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity()`。

可发布：**否（No）**
