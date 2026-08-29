## English

### (a) Scope

Adds the first default-off interpreter allocation and constructor-invoke
slice. ISA v4 gains appended reference `DUP` (53), `NEW` (54), and
constructor-only `INVOKESPECIAL` (55), with explicit class/constructor side
tables. The JNI dispatcher keeps allocation and construction separate through
`AllocObject` and `CallNonvirtualVoidMethodA`, supports `int`/`long`/reference
constructor arguments, and routes JNI exceptions through the existing ordered
exception table.

### (b) Ship-ready?

**No.**

### (c) Review required?

**Yes.** Sol-only review is acceptable.

### (d) Preconditions

- Opcodes 1–52 are numerically unchanged; `ATHROW` remains 52.
- Java and C++ remain on ISA v4 with exact-match rejection.
- The default backend remains `cpp`; `--codegen` remains `legacy`.
- The default-off `diff -r` gate must exit 0.
- Interpreter admission remains static-only.
- Fields and virtual/static/interface invokes remain unsupported.

### Verification

The required combined Gradle command passed 134 tests with 0 skipped,
failures, or errors. The strict C++17 runtime harness completed 67 numbered
checks. The default-versus-explicit-C++ `diff -r` exited 0 with no output.

## 中文

### （a）范围

加入首个默认关闭的解释器对象分配与构造器调用增量。ISA v4 仅在现有操作码
之后追加引用 `DUP`（53）、`NEW`（54）和仅限构造器的
`INVOKESPECIAL`（55），并使用显式的类与构造器侧表。JNI 调度器通过
`AllocObject` 与 `CallNonvirtualVoidMethodA` 将分配和构造保持为两个独立
步骤，支持 `int`、`long` 和引用构造器参数，并将 JNI 异常交给现有的有序
异常表处理。

### （b）可直接发布？

**否。**

### （c）需要审查？

**是。** 可仅由 Sol 审查。

### （d）前置条件

- 操作码 1–52 的数值保持不变；`ATHROW` 仍为 52。
- Java 与 C++ 继续使用 ISA v4，并要求版本完全匹配。
- 默认后端仍为 `cpp`；`--codegen` 仍为 `legacy`。
- 默认关闭的 `diff -r` 门禁必须以 0 退出。
- 解释器仍只接收静态方法。
- 字段操作以及虚、静态、接口调用仍不支持。

### 验证

必需的 Gradle 组合命令共通过 134 项测试，跳过、失败和错误均为 0。
严格的 C++17 运行时测试完成 67 项编号检查。默认输出与显式 C++ 输出之间的
`diff -r` 以 0 退出且无差异输出。
