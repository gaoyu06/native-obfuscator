# EN

## Scope

- Admits exactly four levels of the existing proven constructor chain-input
  walker, including mixed trees with inner or outer `IDIV`/`IREM`.
- Raises only `MAX_PROVEN_INT_CHAIN_BINARY_LEVELS` from 3 to 4. The same
  recursive proof still consumes one explicit budget level per binary node;
  arithmetic remains in the retained JVM bytecode prefix.
- Adds admission, JVM-verification, and Java/native parity coverage for the
  former `four-level-iadd` and `four-level-idiv-inner` leftovers plus an
  outer-`IDIV` four-level tree.
- Adds fail-closed fixtures for `five-level-iadd`,
  `five-level-idiv-inner`, a four-level extra-local operand, an unrelated
  static-invoke operand, and non-int-family computed inputs.

## Still rejected

- Five-or-more nested binaries.
- Extra-local and other unproven operands.
- Skip-super paths.
- Remaining mixed catch placements, including spanning and chain-covering
  tables.
- More than eight distinct paths.
- Extras unassigned on a bridge-taking path.
- Unsafe `ConstantDynamic` shapes.
- `jsr` / `ret`.

The `--codegen` default remains `legacy`. No second walker or compiler pipeline
was added.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML:

- `IrCompilerTest`: 278 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 285 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**.

# 中文

## 范围

- 只接纳现有、已证明的构造器 chain-input walker 的恰好第四层，包括内部或
  外部含 `IDIV` / `IREM` 的混合表达式树。
- 仅把 `MAX_PROVEN_INT_CHAIN_BINARY_LEVELS` 从 3 提高到 4。仍使用同一个
  有界递归证明；每遇到一个二元节点就消耗一层显式预算。算术继续在 JVM
  保留前缀字节码中执行。
- 为原有的 `four-level-iadd`、`four-level-idiv-inner` 缺口和一个外层
  `IDIV` 的四层表达式树增加接纳、JVM 验证及 Java/native 行为一致性测试。
- 增加 fail-closed 用例：`five-level-iadd`、`five-level-idiv-inner`、
  四层树中的额外局部变量操作数、无关静态调用操作数，以及非 int-family
  的计算输入。

## 仍然拒绝

- 五层及以上的二元表达式树。
- 额外局部变量操作数和其他未经证明的操作数。
- 跳过 super 调用的路径。
- 剩余混合 catch 布局，包括跨 suffix 和覆盖 chain call 的异常表。
- 超过 8 条不同路径。
- 在会进入 bridge 的路径上未赋值的 extra。
- 不安全的 `ConstantDynamic` 形状。
- `jsr` / `ret`。

`--codegen` 默认值仍为 `legacy`。没有增加第二套 walker 或编译流水线。

## 验证

执行了上面的父级验证命令。JUnit XML 结果：

- `IrCompilerTest`：278 个测试，0 failure，0 error，0 skipped。
- `CodegenModeTest`：7 个测试，0 failure，0 error，0 skipped。
- 合计：285 个测试，0 failure，0 error，0 skipped。

可直接上线：**否**。

Pushed branch / 已推送分支: `cursor/ir-ctor-four-level-6d81-c89a`

HEAD SHA at verification / 验证时 HEAD: `c94ecc2eddbaf3ccfd43d75b7dd9e86cdf07766b`

Files changed / 修改文件:

- `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`
- `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
- `docs/architecture/ir-flex-ctor-status.md`
- `PR_BODY.md`
