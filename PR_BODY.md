# Admit intra-prefix constructor control flow for the this/super split

## (a) Scope

`ConstructorSpecialMethodProcessor.split()` previously rejected **any** branch
(`JumpInsnNode`, `TableSwitchInsnNode`, `LookupSwitchInsnNode`) at or before the
verifier-required this/super `INVOKESPECIAL <init>` call. This change narrows
that guard so a prefix-local branch — one whose every target label is also in
the prefix (instruction index `<= callIndex`) — is admitted, while a prefix
branch whose target lands in the suffix is still rejected because it would skip
the mandatory chain call.

The retained bytecode still holds the uninitialized-this prefix plus the
this/super call. `createNativeBody` still emits the initialized-this suffix
only, and `postProcess` still appends the hidden static native bridge
invocation. No uninitialized-this prefix code is IR-lowered; `IrMethodCompiler`
is unchanged. Label cloning already maps every label, so cloned prefix branches
resolve correctly.

This unblocks the JDK 25 leftover constructor
`Main$Validated.<init>(I)V` in
`obfuscator/test_data/tests/jdk25/FlexibleConstructorBodiesE2E/Main.java`, whose
`if (normalized == 0) throw ...; super(normalized);` prologue compiles to an
`IFNE` (opcode 154) with a prefix-local target. On master `5ac6ec5` that
constructor was left as Java bytecode with:

```
Control flow before the this/super call cannot be split safely
at bytecode instruction 8 (opcode 154)
```

### Files changed

- `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`
  — replace the blanket prefix-branch rejection with the prefix-local target
  rule.
- `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java` — add one
  positive and four negative constructor-split cases plus builders/helpers.
- `docs/architecture/ir-flex-ctor-status.md` — facts-only status note.
- `PR_BODY.md` — this file.

### Behavior

- ALLOW prefix jumps/switches whose every target label is in the prefix.
- REJECT a prefix jump/switch whose target is in the suffix
  (`Constructor prefix branches across the this/super call`).
- REJECT suffix jumps/switches into the prefix (unchanged).
- REJECT try/catch crossing the split (unchanged).
- REJECT prefix `ASTORE` of forwarded reference locals (unchanged).
- REJECT multiple this/super candidates (unchanged).

`--codegen` default remains `legacy`. No CLI defaults, interpreter/evaluator,
`--ir-lower`/`--backend`, loader/manifest, or README support badges were
touched.

### Tests

```
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

- `by.radioegor146.ir.IrCompilerTest`: 102 tests, 0 failures, 0 skipped.
- `by.radioegor146.CodegenModeTest`: 7 tests, 0 failures, 0 skipped.

## (b) Ship-ready?

**No.** This is a compiler admission-rule change validated only by synthetic
bytecode unit tests under JDK 21. It does not include a JDK 25 end-to-end run
(that needs a Temurin 25 toolchain) and makes no claim of JDK 25 support.

## (c) Review required?

Yes.

## (d) Review preconditions

- Confirm the prefix-local vs. suffix branch classification is correct for
  switch instructions (default + all case labels) as well as conditional and
  unconditional jumps.
- Confirm that cloned prefix branches remain verifiable in the rewritten
  constructor (the positive test exercises `IFNE`; broader shapes such as
  backward prefix loops and switches are covered structurally by `split()` but
  not executed end-to-end).
- A JDK 25 end-to-end pass on a Temurin 25 toolchain is still required before
  claiming any JDK 25 support; do not update README support badges based on this
  change alone.

---

# 中文

## (a) 范围

`ConstructorSpecialMethodProcessor.split()` 之前会拒绝在验证器要求的
this/super `INVOKESPECIAL <init>` 调用之前的**任何**分支（`JumpInsnNode`、
`TableSwitchInsnNode`、`LookupSwitchInsnNode`）。本次修改收窄该限制：当分支的
所有目标标签都位于前缀内（指令下标 `<= callIndex`）时，允许该前缀内部分支；
若前缀分支的目标落在后缀，则仍然拒绝，因为那会跳过必需的构造链调用。

保留的字节码仍包含未初始化 this 的前缀以及 this/super 调用。`createNativeBody`
仍只生成已初始化 this 的后缀，`postProcess` 仍追加隐藏的静态原生桥接调用。不对
未初始化 this 的前缀代码做 IR 降级；`IrMethodCompiler` 未改动。标签克隆本就映射
了全部标签，因此克隆后的前缀分支能正确解析。

这解除了 JDK 25 遗留构造器
`Main$Validated.<init>(I)V`
（位于 `obfuscator/test_data/tests/jdk25/FlexibleConstructorBodiesE2E/Main.java`）
的阻塞：其 `if (normalized == 0) throw ...; super(normalized);` 序言会编译为
目标位于前缀内的 `IFNE`（操作码 154）。在 master `5ac6ec5` 上，该构造器因下述
原因被保留为 Java 字节码：

```
Control flow before the this/super call cannot be split safely
at bytecode instruction 8 (opcode 154)
```

### 行为

- 允许：前缀跳转/switch 的所有目标标签都在前缀内。
- 拒绝：前缀跳转/switch 的目标落在后缀
  （`Constructor prefix branches across the this/super call`）。
- 拒绝：后缀跳转/switch 跳回前缀（不变）。
- 拒绝：try/catch 跨越切分点（不变）。
- 拒绝：前缀对转发引用局部变量的 `ASTORE`（不变）。
- 拒绝：存在多个 this/super 候选（不变）。

`--codegen` 默认仍为 `legacy`。未改动任何 CLI 默认值、解释器/求值器、
`--ir-lower`/`--backend`、加载器/清单，或 README 支持徽章。

### 测试

```
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：`BUILD SUCCESSFUL`。

- `by.radioegor146.ir.IrCompilerTest`：102 个测试，0 失败，0 跳过。
- `by.radioegor146.CodegenModeTest`：7 个测试，0 失败，0 跳过。

## (b) 可发布？

**否。** 这是一处编译器准入规则的修改，仅通过 JDK 21 下的合成字节码单元测试验证。
不包含 JDK 25 端到端运行（需要 Temurin 25 工具链），也不声称支持 JDK 25。

## (c) 是否需要评审？

需要。

## (d) 评审前置条件

- 确认对 switch 指令（default 加所有 case 标签）以及条件/无条件跳转的
  “前缀内 vs. 后缀” 分类判断正确。
- 确认改写后的构造器中，克隆的前缀分支仍可通过验证（正向测试覆盖 `IFNE`；
  更广的形态，如前缀内向后循环与 switch，`split()` 从结构上覆盖，但未做端到端执行）。
- 在声称任何 JDK 25 支持之前，仍需在 Temurin 25 工具链上完成 JDK 25 端到端验证；
  不要仅凭本次修改更新 README 支持徽章。
