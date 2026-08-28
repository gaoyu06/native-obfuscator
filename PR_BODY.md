## English

### Scope

Blinded reader notes for the live-mix `published.so`, including per-method
native-code reconstruction and post-recovery scoring.

### Ship-ready?

No. This is an N=1 evaluation artifact, not a production change.

### Review required?

Yes — confirm the blinding order: `recovery.md` was committed before
`published.jar`, `run.md`, or the fixture source was opened.

### Preconditions

Do not cite this result as a bar pass/fail without checking the committed
recovery against the fixture source.

## 中文

### 范围

针对 live-mix `published.so` 的盲读记录，包括逐方法的原生代码恢复，以及在恢复结果提交后的评分。

### 可以直接发布吗？

不可以。这是一个 N=1 的评估产物，不是生产代码变更。

### 需要审查吗？

需要——请确认盲测顺序：先提交 `recovery.md`，之后才打开
`published.jar`、`run.md` 或测试夹具源码。

### 前置条件

在将本结果引用为门槛通过/失败的依据之前，必须先核对已提交的恢复结果与测试夹具源码。

# Live direct-IR mix artifact / 可存活的 direct-IR mix 产物

## (a) Scope / 范围

This change publishes a small JNI/C++ compiler fixture, its direct-IR
transpiled jar, a stripped GCC Release `.so`, and liveness evidence. The
`mix(II)I` result observably depends on both inputs; oracle and native stdout
match, and the stripped implementation retains integer multiply, shift,
bitwise, and add operations.

本变更发布一个小型 JNI/C++ 编译器 fixture、其 direct-IR 转译 jar、经 strip 的
GCC Release `.so`，以及存活性证据。`mix(II)I` 的结果可观察地依赖两个输入；
Java oracle 与 native stdout 完全一致，且 strip 后的实现仍保留整数乘法、移位、
位运算和加法指令。

This is an artifact-preparation change only. It adds no packing, encryption,
interpreter, reader, recovery, or scoring implementation.

本变更仅准备编译产物，不包含打包、加密、解释器、reader、恢复或评分实现。

## (b) Ship-ready? / 可直接发布？

**No.** This is a controlled evaluation artifact, not a production release.

**否。** 这是受控评估产物，不是生产版本。

## (c) Review required? / 是否需要审查？

**Yes.** Before any later reader cites this artifact, review must confirm that
`mix` is live: check the diverse runtime output and the non-constant arithmetic
path in the stripped-library disassembly.

**是。** 任何后续 reader 引用此产物前，审查者必须确认 `mix` 仍然存活：检查
多样化的运行时输出，并确认 strip 后库的反汇编中存在非常量算术路径。

## (d) Preconditions / 前置条件

- Re-run the Java-oracle/native stdout comparison and require `cmp` exit `0`.
- Inspect the stripped `mix` symbol and its disassembly for live arithmetic.
- Do not treat this artifact or its liveness notes as a reader/recovery result.

- 重新运行 Java oracle/native stdout 对比，并要求 `cmp` 退出码为 `0`。
- 检查 strip 后的 `mix` 符号及其反汇编，确认存在存活的算术运算。
- 不得把此产物或其存活性记录视为 reader/恢复结果。
