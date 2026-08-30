<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Leftover admitted

- Admits only the former `long-extra-local` leftover: an extra-local long
  chain-input leaf whose prefix `LSTORE` is a direct copy of a declared
  `LLOAD`.
- Requires exactly one dominating overlapping write through the final chain
  call and the exact long local state at every chain call, including wide-slot
  overlap checks.
- Keeps all long arithmetic in the retained JVM bytecode prefix and reuses one
  hidden bridge plus the existing path id.

## Still rejected

- Extra-local int chain operands.
- Extra-local long shift counts and shift values.
- Extra-local long operands of `LDIV` or `LREM`.
- `LNEG` of an extra-local, computed value, or constant.
- Five-or-more nested binary levels.
- Other unlisted constructor-chain inputs.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals:

- `IrCompilerTest`: 359 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 366 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**

# 中文

## 已准入的遗留项

- 仅接纳原 `long-extra-local` 遗留项：long 构造器链输入的额外局部变量
  叶子，且其前缀 `LSTORE` 必须直接复制已声明参数的 `LLOAD`。
- 要求直到最后一次链调用为止恰好存在一次支配所有调用路径的重叠写入，
  且每次链调用处都必须是精确的 long 局部变量状态，包括宽槽位重叠检查。
- 所有 long 运算仍保留在 JVM 字节码前缀中，并复用单个隐藏桥接方法及
  现有路径编号。

## 仍然拒绝

- 使用额外局部变量的 int 构造器链操作数。
- 使用额外局部变量的 long 移位计数或移位值。
- `LDIV` 或 `LREM` 使用额外局部变量的 long 操作数。
- 对额外局部变量、计算值或常量执行 `LNEG`。
- 五层及以上的嵌套二元运算。
- 其他未列出的构造器链输入。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：

- `IrCompilerTest`：359 项测试，0 项失败，0 项错误，0 项跳过。
- `CodegenModeTest`：7 项测试，0 项失败，0 项错误，0 项跳过。
- 合计：366 项测试，0 项失败，0 项错误，0 项跳过。

可发布：**否**

<!-- CURSOR_AGENT_PR_BODY_END -->
