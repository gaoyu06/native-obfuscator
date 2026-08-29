<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Leftover admitted

- Raises only the fail-closed double constructor-chain binary budget from three
  levels to four levels.
- Admits the former `double-four-level-dadd` leftover plus inner and outer
  `DDIV` four-level trees.
- Keeps all admitted double arithmetic in the retained JVM bytecode prefix and
  preserves one hidden bridge with one path id.

## Still rejected

- Five-or-more nested double binary levels.
- Extra-local double operands and unsafe `DNEG` operands.
- Existing five-or-more int, long, and float binary levels and all other
  unlisted constructor-chain inputs.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-produced JUnit XML totals:

- `IrCompilerTest`: 350 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 357 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**

# 中文

## 已准入的遗留项

- 仅将构造函数链路中 double 二元表达式的故障闭合预算从三层提高到四层。
- 准入原有的 `double-four-level-dadd` 遗留项，并覆盖内层与外层 `DDIV`
  四层表达式树。
- 所有已准入的 double 算术仍保留在 JVM 字节码前缀中，并保持一个隐藏桥接
  方法和一个路径编号。

## 仍然拒绝

- 五层及以上的 double 二元表达式嵌套。
- 使用额外局部变量的 double 操作数，以及不安全的 `DNEG` 操作数。
- 既有的五层及以上 int、long、float 二元表达式，以及其他未列出的构造函数
  链路输入。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

子代理生成的 JUnit XML 汇总：

- `IrCompilerTest`：350 项测试，0 项失败，0 项错误，0 项跳过。
- `CodegenModeTest`：7 项测试，0 项失败，0 项错误，0 项跳过。
- 合计：357 项测试，0 项失败，0 项错误，0 项跳过。

可发布：**否**

<!-- CURSOR_AGENT_PR_BODY_END -->
