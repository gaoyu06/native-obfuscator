# Bounded long constructor-chain inputs / 有界 long 构造器链输入

## Summary / 摘要

**(a) Leftover admitted / 已接纳缺口**

- Admit leaf-only `LSUB` and `LMUL` as long constructor chain-call inputs.
  Both operands must remain proven long leaves: a declared-long `LLOAD`,
  `LCONST_0`, `LCONST_1`, or `LDC` of `Long`.
- 接纳 leaf-only `LSUB` 与 `LMUL` 作为 long 构造器链调用参数。两个操作数
  仍必须是已证明的 long 叶子：声明的 long 参数 `LLOAD`、`LCONST_0`、
  `LCONST_1` 或 Long 类型的 `LDC`。
- The dedicated long binary budget remains exactly 1; arithmetic stays in the
  retained JVM bytecode prefix.
- long 二元深度预算仍严格为 1；运算继续在保留的 JVM 字节码前缀中执行。

## Still rejected / 仍然拒绝

**(b) Fail-closed boundaries / Fail-closed 边界**

- Nested long binaries, including nested `LADD`/`LSUB`/`LMUL`; extra-local
  long operands; `LDIV`/`LREM`; long bitwise operations and shifts; `LNEG`.
- 嵌套 long 二元表达式（包括嵌套 `LADD`/`LSUB`/`LMUL`）、额外局部变量
  long 操作数、`LDIV`/`LREM`、long 位运算与移位、`LNEG`。
- Float, double, and reference computed inputs; int-family trees with five or
  more binary levels.
- float、double、reference 计算输入，以及五层或更多层的 int-family
  二元表达式树。
- Skip-super paths, remaining mixed catch placements, more than eight paths,
  extras unassigned on a bridge-taking path, unsafe condy, and `jsr`/`ret`.
- skip-super 路径、剩余 mixed catch 布局、超过八条路径、bridge 路径上未赋值
  的 extras、不安全 condy，以及 `jsr`/`ret`。

## Verification / 验证

**(c) Parent verification command / 父级验证命令**

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results / JUnit XML 结果：

- `IrCompilerTest`: 285 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total / 总计: 292 tests, 0 failures, 0 errors, 0 skipped.

The gate includes dedicated IR admission, rewritten-constructor JVM
verification, and compile-and-run Java parity for both `LSUB` and `LMUL`.
该门禁包含 `LSUB` 与 `LMUL` 的专用 IR 接纳测试、重写构造器 JVM 验证，
以及编译运行 Java 行为一致性测试。

## Readiness / 就绪状态

**(d) Ship-ready / 可直接上线：No / 否。**

Pushed branch / 已推送分支: `cursor/ir-ctor-lsub-lmul-6d81-d927`

Tested HEAD SHA / 已验证 HEAD 提交（随后仅提交本 PR body）:
`a7d82bdc81b27424a9b41cba33c731f2912e57e6`

Files changed / 变更文件:
- `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`
- `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
- `docs/architecture/ir-flex-ctor-status.md`
- `PR_BODY.md`
