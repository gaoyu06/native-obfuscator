# IR compiler phase 12 / IR 编译器第十二阶段

Required base / 必须基于:
`cursor/ir-compiler-phase11-6d81`
(`6fc64927a53c777a36c38e54aaed01b1bd696ed3`, draft PR #78).
The docs-only phase-11 review branches #82/#83 are not used. /
必须基于 `cursor/ir-compiler-phase11-6d81`
（`6fc64927a53c777a36c38e54aaed01b1bd696ed3`，草稿 PR #78），
不使用仅含文档的 phase-11 审阅分支 #82/#83。

## Summary / 摘要

Phase 12 admits supported user/test class constructor bodies into the optional
Java bytecode → typed CFG IR → C++/JNI compiler. The complete constructor is
validated before mutation. The verifier-required `this(...)`/`super(...)` call
stays in the Java constructor, while the initialized suffix runs through a
hidden static native bridge with local 0 represented as `REFERENCE`. The
constructor itself never receives illegal `ACC_NATIVE`. The default remains
`legacy`.

第十二阶段将受支持的用户/测试类构造函数方法体纳入可选的 Java 字节码 → typed
CFG IR → C++/JNI 编译路径。完整构造函数会在 mutation 前验证。verifier 要求的
`this(...)`/`super(...)` 调用保留在 Java 构造函数中；初始化后的后缀通过 hidden
static native bridge 执行，local 0 表示为 `REFERENCE`。构造函数本身不会被非法地
标记为 `ACC_NATIVE`。默认值仍为 `legacy`。

## (a) Change scope / 本次改动范围

- Makes `MethodProcessor.shouldProcess` codegen-mode aware: `<init>` remains
  excluded in `legacy` and is considered only in explicit IR mode.
- Validates the complete constructor with the existing frontend before
  creating output, cache entries, a hidden bridge, or rewritten bytecode.
- Requires one linear direct `this(...)` or `super(...)` call, then lowers the
  independently valid initialized suffix through a hidden static native bridge.
- Keeps the constructor non-native and passes initialized `this` as local 0 /
  `REFERENCE` / `jobject obj`.
- Retains the existing full-IR `<init>` representation and
  `CallNonvirtualVoidMethod` family. The executable bridge preserves the
  verifier-required call in bytecode so it is not executed twice.
- Reuses exact phase-10 `SetIntField`, `SetLongField`, and `SetObjectField`
  carriers for `I`, `J`, object, and array stores.
- Keeps unprotected exceptional exits as JNI `void` returns with the exception
  pending.
- Leaves an unsupported constructor as unchanged Java bytecode instead of
  sending it to the constructor-excluding legacy method shell.
- Adds simple-field/getter, subclass `super(...)`, delegated `this(...)`,
  object/array field, fallback-before-mutation, and retained g++ smoke coverage.
- Preserves phase-9 array returns, phase-10 fields, phase-11 invokes, the
  `legacy` default, and all snippet resources.

- 使 `MethodProcessor.shouldProcess` 感知 codegen mode：`<init>` 在 `legacy`
  中仍被排除，仅在显式 IR mode 下成为候选。
- 在创建输出、cache entry、hidden bridge 或改写字节码前，先用现有 frontend
  验证完整构造函数。
- 要求存在一个线性的直接 `this(...)` 或 `super(...)` 调用，再将可独立验证的
  已初始化后缀通过 hidden static native bridge 降级。
- 构造函数保持非 native；已初始化的 `this` 作为 local 0 / `REFERENCE` /
  `jobject obj` 传入。
- 保留完整 IR 中现有的 `<init>` 表示及 `CallNonvirtualVoidMethod` family。
  可执行 bridge 将 verifier 要求的调用保留在字节码中，避免重复执行构造函数。
- 对 `I`、`J`、对象及数组字段写入复用 phase-10 的精确 `SetIntField`、
  `SetLongField` 与 `SetObjectField` carrier。
- 无保护异常出口继续以 JNI `void` 返回，并保持异常 pending。
- 不受支持的构造函数保持原 Java 字节码，不会送入本就排除构造函数的 legacy
  method shell。
- 新增简单字段/ getter、子类 `super(...)`、委托 `this(...)`、对象/数组字段、
  mutation 前 fallback 及保留的 g++ smoke 覆盖。
- 保留 phase-9 数组返回、phase-10 字段、phase-11 invoke、`legacy` 默认值及
  全部 snippet resources。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

This remains a partial, opt-in compiler slice. Constructors with unsupported
operations, non-linear initialization prefixes, cross-split exception regions,
or suffix dependencies on prefix-only locals remain as Java bytecode.
Float/double, invokedynamic, `POP2`, `MULTIANEWARRAY`, other primitive field or
invoke carriers, and other operations outside the documented subset still
fall back. Focused unit tests and C++ syntax checks do not replace native
runtime-parity gates on every supported platform.

这仍是部分、可选的编译器增量。含不支持操作、非线性初始化前缀、跨 split
异常区域，或后缀依赖仅在前缀初始化的局部变量的构造函数仍保留为 Java 字节码。
float/double、invokedynamic、`POP2`、`MULTIANEWARRAY`、其他 primitive 字段或
invoke carrier，以及文档范围外的操作仍会 fallback。聚焦单测与 C++ 语法检查
不能替代全部受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review must confirm the verifier-safe split, full-body admission before
mutation, exact local-0 receiver mapping, constructor bridge descriptor and
argument order, retained `this(...)`/`super(...)` bytecode call, I/J/reference
field carriers, JNI void exceptional exits, and unchanged-constructor fallback.
The stacked phase-9 through phase-11 regressions must remain present.

Review 必须确认 verifier-safe split、mutation 前的完整方法体 admission、精确的
local-0 receiver 映射、构造函数 bridge 描述符与参数顺序、保留的
`this(...)`/`super(...)` 字节码调用、I/J/引用字段 carrier、JNI void 异常出口，
以及构造函数保持不变的 fallback。堆叠的 phase-9 至 phase-11 回归必须保留。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-compiler-phase11-6d81` at `6fc6492…` (draft
   PR #78), not `master` or docs-only review branches #82/#83.
   必须基于 `cursor/ir-compiler-phase11-6d81` 的 `6fc6492…`（草稿 PR #78）
   比较，不得改用 `master` 或仅含文档的审阅分支 #82/#83。
2. Re-run the focused command with `CC=gcc CXX=g++ --rerun-tasks` and inspect
   the JUnit XML. Recorded result: `IrCompilerTest` 56 plus
   `CodegenModeTest` 2, total 58; zero skipped, failures, or errors.
   使用 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦命令并读取 JUnit XML。
   记录结果为 `IrCompilerTest` 56 加 `CodegenModeTest` 2，共 58；跳过、失败、
   错误均为零。
3. With g++ and JNI headers present, require
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` to be unskipped
   and independently run `g++ -std=c++17 -fsyntax-only` on the retained unit.
   The recorded 61-method smoke and independent check both exited zero.
   当 g++ 与 JNI headers 存在时，必须确认
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过，并对保留的
   unit 独立运行 `g++ -std=c++17 -fsyntax-only`。记录的 61-method smoke 与
   独立检查均以零退出。
4. Inspect a successful constructor rewrite: `<init>` must remain non-native,
   retain exactly its direct `this(...)`/`super(...)` prefix, invoke the hidden
   bridge with initialized `this` plus descriptor arguments, and return `V`.
   检查成功的构造函数改写：`<init>` 必须保持非 native，精确保留其直接
   `this(...)`/`super(...)` 前缀，以已初始化的 `this` 加描述符参数调用 hidden
   bridge，并以 `V` 返回。
5. Inspect a rejected constructor after an unsupported opcode. Its instruction
   list and `ACC_NATIVE` state, output/native metadata, hidden bridge state, and
   all four caches must remain unchanged.
   检查因不支持 opcode 被拒绝的构造函数：其指令列表、`ACC_NATIVE` 状态、
   output/native metadata、hidden bridge 状态及四类 cache 均必须保持不变。
6. Verify source and tests still retain the `legacy` CLI/API default, phase-9
   `jarray` return cast, phase-10 field families, phase-11 invoke families, and
   `sources/cppsnippets.properties`.
   确认源码与测试仍保留 `legacy` CLI/API 默认值、phase-9 `jarray` 返回转换、
   phase-10 字段 family、phase-11 invoke family 及
   `sources/cppsnippets.properties`。

Detailed evidence / 详细证据:
`docs/architecture/ir-phase12-status.md`.
