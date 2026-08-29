# Bounded nested IDIV/IREM constructor chain inputs / 有界嵌套 IDIV/IREM 构造器链输入

## Summary / 摘要

- Admits the targeted leftover: `IDIV` and `IREM` as inner nodes of the
  already-capped three-level constructor chain-input walker.
- 接纳本次目标缺口：允许 `IDIV` 与 `IREM` 作为已有三层深度上限的构造器链
  输入树内部节点。
- Uses the same explicit depth budget of three for non-trapping binaries and
  `IDIV`/`IREM`; leaf-only div/rem remains supported as a special case. No
  unbounded recursive walk or fourth level was added.
- 非捕获二元运算与 `IDIV`/`IREM` 共用原有的显式三层预算；原有仅叶子
  div/rem 仍是该规则的特例。没有新增无界递归或第四层。
- These expressions remain in the retained JVM bytecode prefix, preserving
  Java divide-by-zero and signed-overflow behavior rather than lowering the
  chain-input arithmetic into C++ IR.
- 这些表达式仍在保留的 JVM 字节码前缀中执行，继续保持 Java 除零与有符号
  溢出语义，而不是把链输入算术下沉到 C++ IR。

## Positive and fail-closed coverage / 正向与失败关闭覆盖

Positive bytecode-builder coverage includes:

- `(arg / 2) + 1` (`idiv-inner`);
- `(arg % 3) * 2` (`irem-inner`);
- `(arg + 1) / 2` (`nested-idiv`);
- `(arg * arg) % 3` (`nested-irem`);
- `(arg / 2) / 3` (nested `IDIV`);
- `((arg / 2) + 1) * 3` (three-level mixed tree).

正向字节码构造测试覆盖上述两种内节点方向、以已证明二元表达式为操作数的
外层 div/rem、div/rem 自身嵌套，以及三层混合树。测试会构建 IR 后缀、
确认单一隐藏桥被调用，并执行原始 Java 与生成 JNI 库的行为一致性检查。

Still rejected / 仍拒绝：

- four-or-more nested binaries, including mixed trees with inner `IDIV` /
  四层及以上二元树，包括含内部 `IDIV` 的混合树；
- extra-local `IDIV`/`IREM` operands and extra-local operands inside a
  non-trapping binary containing inner div/rem /
  `IDIV`/`IREM` 的额外局部变量操作数，以及包含内部 div/rem 的非捕获二元树
  中的额外局部变量；
- unproven operands such as unrelated static invokes / 未证明的操作数（如无关
  静态调用）；
- skip-super paths / 跳过 super 的路径；
- remaining mixed catch placements, including spanning ranges and ranges that
  cover a chain call / 剩余混合 catch 布局，包括跨范围及覆盖链调用的范围；
- more than eight paths / 超过八条路径；
- unassigned extras on bridge-taking paths / 调用桥路径上未赋值的额外局部变量；
- unsafe condy shapes / 不安全 condy 形状；
- `jsr`/`ret`.

## Verification / 验证

Parent verification command / 父级验证命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results / JUnit XML 结果：

- `IrCompilerTest`: 274 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total / 合计: 281 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready / 可直接上线：**No / 否**

Pushed branch / 已推送分支: `cursor/ir-ctor-idiv-inner-6d81-7fa0`
HEAD SHA (verified implementation before this metadata-only file) / HEAD SHA（本元数据文件之前的已验证实现）: `f226cc0234dc1967c1dcfaee1f5c260d53fd4fc5`
Files changed / 变更文件: `PR_BODY.md`, `docs/architecture/ir-flex-ctor-status.md`, `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`, `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
