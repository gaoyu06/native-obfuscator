## (a) Problem / 问题

Constructors with multiple direct this/super `<init>` candidates were rejected
even when control flow formed a verifier-safe diamond and every successful path
executed exactly one chain call before one shared initialized-this suffix.

当构造器包含多个直接 this/super `<init>` 候选调用时，即使控制流形成可验证的菱形、
每条成功路径只执行一次链式调用并进入同一个 initialized-this 后缀，之前也会被拒绝。

## (b) Change / 变更

Admit only a strict shared-label diamond. Every non-final chain call must be
immediately followed by `GOTO` to one common join label, and the final call must
fall through to that label. A count-state CFG proof rejects zero-call,
repeated-call, unreachable-candidate, cross-split branch, `ASTORE 0`, `jsr`/`ret`,
and cross-split try/catch shapes. Both chain calls remain in retained bytecode;
the shared suffix is emitted once behind one hidden static bridge.

仅接纳严格的单共享标签菱形。每个非末尾链式调用后必须立即以 `GOTO` 跳转到同一个
join 标签，末尾调用必须自然落入该标签。基于调用次数状态的 CFG 证明会拒绝零调用、
重复调用、不可达候选、跨 split 分支、`ASTORE 0`、`jsr`/`ret` 和跨 split
try/catch。两个链式调用都保留在字节码中，共享后缀只通过一个隐藏静态桥接生成一次。

## (c) Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: `IrCompilerTest` 136 tests and `CodegenModeTest` 7 tests; 143 total,
0 failures, 0 errors, 0 skipped. The gate includes JVM verification and CMake +
g++ compile/run parity under `-Xverify:all -Xcheck:jni`.

JUnit XML：`IrCompilerTest` 136 项、`CodegenModeTest` 7 项；共 143 项，
0 failures、0 errors、0 skipped。测试包含 JVM 验证，以及在
`-Xverify:all -Xcheck:jni` 下的 CMake + g++ 编译运行一致性。

## (d) Release decision / 发布决定

Ship-ready: **No**. This is a bounded constructor-split admission, with no
stacked review and no default flip. `--codegen legacy`, `--ir-lower direct`,
and `--backend cpp` remain unchanged. Separate returns, distinct joins,
post-call prefix work, and non-identical per-call suffixes remain rejected.

可发布：**否**。本变更仅扩大一个有边界的构造器 split 接纳范围；没有堆叠审查，
也没有默认值切换。`--codegen legacy`、`--ir-lower direct` 和
`--backend cpp` 均保持不变。独立 return、不同 join、调用后前缀逻辑以及不一致的
逐调用后缀仍会被拒绝。
