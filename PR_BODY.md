<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Leftover admitted

- Admits only the former `float-extra-local` leftover: an extra-local float
  chain-input leaf whose prefix `FSTORE` is a direct copy of a declared
  `FLOAD`.
- Requires exactly one such dominating store before the first chain call and
  rejects any overlapping overwrite through the final chain call.
- Keeps all float arithmetic in the retained JVM bytecode prefix and reuses
  one hidden bridge plus the existing path id.

## Still rejected

- Extra-local int and long chain operands.
- `FNEG` of an extra-local, computed value, or constant.
- Extra-local stores fed by computed trees or another extra local.
- Five-or-more nested binary levels in the int, long, float, and double
  families.
- Other unlisted constructor-chain inputs.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals will be recorded after the required verification run.

Ship-ready: **No**

# 中文

## 已准入的遗留项

- 仅接纳原 `float-extra-local` 遗留项：float 构造器链输入的额外局部变量
  叶子，且其前缀 `FSTORE` 必须直接复制已声明参数的 `FLOAD`。
- 要求首次构造器链调用前恰好存在一次支配所有调用路径的写入，并拒绝直到
  最后一次链调用为止的任何重叠覆写。
- 所有 float 运算仍保留在 JVM 字节码前缀中，并复用单个隐藏桥接方法及
  现有路径编号。

## 仍然拒绝

- 使用额外局部变量的 int 和 long 构造器链操作数。
- 对额外局部变量、计算值或常量执行 `FNEG`。
- 从计算树或另一个额外局部变量写入的额外局部变量。
- int、long、float 和 double 各族中五层及以上的嵌套二元运算。
- 其他未列出的构造器链输入。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

所需验证运行结束后将记录 JUnit XML 汇总。

可发布：**否**
<!-- CURSOR_AGENT_PR_BODY_END -->
