# IR compiler phase 11 / IR 编译器第十一阶段

Required base / 必须基于:
`cursor/ir-compiler-phase10-6d81`
(`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`).
This is the phase-10 tip containing the typed field work and the retained
phase-9 array-return `jarray` carrier fix. /
这是包含 typed 字段实现及保留的 phase-9 数组返回 `jarray` carrier 修复的
phase-10 tip。

## Summary / 摘要

Phase 11 adds `INVOKEINTERFACE` and non-constructor `INVOKESPECIAL` to the
optional Java bytecode → typed CFG IR → C++/JNI compiler. Both opcodes use only
the existing exact `I`, exact `J`, object/array reference, and `V` invoke
carriers. The default remains `legacy`.

第十一阶段为可选的 Java 字节码 → typed CFG IR → C++/JNI 编译路径增加
`INVOKEINTERFACE` 与非构造器 `INVOKESPECIAL`。两种 opcode 仅使用现有的精确
`I`、精确 `J`、对象/数组引用及 `V` invoke carrier。默认值仍为 `legacy`。

## (a) Change scope / 本次改动范围

- Adds `IrNodes.Invoke.Kind.INTERFACE` and admits `INVOKEINTERFACE` for exact
  `I`, exact `J`, object/array reference, and `V` return descriptors.
- Resolves the interface owner through the existing class cache, uses
  `GetMethodID`, and emits `CallIntMethod`, `CallLongMethod`,
  `CallObjectMethod`, or `CallVoidMethod`.
- Adds non-constructor `INVOKESPECIAL` for the same carrier set and emits
  `CallNonvirtualIntMethod`, `CallNonvirtualLongMethod`,
  `CallNonvirtualObjectMethod`, or `CallNonvirtualVoidMethod` with receiver,
  declaring class, method ID, and arguments.
- Keeps `<init>` calls on the existing `CallNonvirtualVoidMethod` path.
- Validates invoke argument count, argument types, and result type against the
  descriptor. Primitive sorts `Z`, `B`, `C`, `S`, `F`, and `D` remain rejected
  instead of being widened.
- Treats interface calls as potentially throwing and routes null receivers and
  pending JNI exceptions through the block exceptional exit.
- Adds interface and special I/J/reference/V regressions, an interface
  null-receiver catch case, unsupported-descriptor and `invokedynamic`
  rejection, and fallback-before-mutation after both newly admitted calls.
- Preserves the phase-9 array-return and phase-10 field regressions,
  constructor-method exclusion, the `legacy` default, and every existing
  snippet.

- 增加 `IrNodes.Invoke.Kind.INTERFACE`，并为精确 `I`、精确 `J`、对象/数组引用及
  `V` 返回描述符接纳 `INVOKEINTERFACE`。
- 通过现有 class cache 解析接口 owner，使用 `GetMethodID`，并生成
  `CallIntMethod`、`CallLongMethod`、`CallObjectMethod` 或
  `CallVoidMethod`。
- 为相同 carrier 集接纳非构造器 `INVOKESPECIAL`，并以 receiver、声明类、
  method ID 和参数生成 `CallNonvirtualIntMethod`、
  `CallNonvirtualLongMethod`、`CallNonvirtualObjectMethod` 或
  `CallNonvirtualVoidMethod`。
- `<init>` 调用继续使用现有 `CallNonvirtualVoidMethod` 路径。
- 按描述符校验 invoke 参数数量、参数类型及结果类型。primitive sort `Z`、`B`、
  `C`、`S`、`F` 与 `D` 继续被拒绝，不进行拓宽。
- 将接口调用视为可抛异常；null receiver 和 pending JNI 异常均经当前 block
  的异常出口路由。
- 新增接口与 special 的 I/J/引用/V 回归、接口 null-receiver catch、
  不支持描述符与 `invokedynamic` 拒绝，以及两种新调用之后的 mutation 前
  fallback 回归。
- 保留 phase-9 数组返回及 phase-10 字段回归、构造器方法体排除、`legacy`
  默认值及全部现有 snippets。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 11 remains a partial, opt-in compiler slice. Unsupported bytecodes and
descriptors still fall back, including other primitive invoke carriers,
float/double operations, `MULTIANEWARRAY`, non-`int` primitive array
operations, invokedynamic, constructor method bodies, and category-two stack
manipulation. Focused unit and C++ syntax evidence does not replace
supported-platform native runtime-parity gates.

第十一阶段仍是部分、可选的编译器增量。不支持的字节码与描述符继续 fallback，
包括其他 primitive invoke carrier、float/double 操作、`MULTIANEWARRAY`、
非 `int` primitive array 操作、invokedynamic、构造器方法体及 category-two
stack manipulation。聚焦单测与 C++ 语法证据不能替代受支持平台上的 native
运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review must confirm descriptor-exact IR typing, interface lookup and virtual
JNI-family selection, nonvirtual receiver/class/method-ID ordering,
null-receiver exception routing, and fallback-before-mutation. The stacked
phase-9 array-return and phase-10 field regressions must remain present.

Review 必须确认描述符精确的 IR typing、接口 lookup 与 virtual JNI family 选择、
nonvirtual receiver/class/method-ID 顺序、null receiver 异常路由，以及 mutation
前 fallback。堆叠基线中的 phase-9 数组返回与 phase-10 字段回归必须保留。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-compiler-phase10-6d81` at `b8cdb8e…`, not
   `master` or the docs-only review branches.
   必须基于 `cursor/ir-compiler-phase10-6d81` 的 `b8cdb8e…` 比较，不得改用
   `master` 或 docs-only review 分支。
2. Re-run the focused Gradle command with `CC=gcc CXX=g++ --rerun-tasks` and
   inspect the actual JUnit XML counts. Recorded result: `IrCompilerTest` 53
   plus `CodegenModeTest` 2, total 55; zero skipped, failures, or errors.
   使用 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦 Gradle 命令，并检查实际 JUnit
   XML 计数。记录结果为 53 + 2，共 55 个测试；跳过、失败、错误均为零。
3. With g++ and JNI headers present, require
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` to remain
   unskipped and independently run `g++ -std=c++17 -fsyntax-only` on the exact
   retained generated translation unit. Recorded result: the 59-method smoke
   and independent syntax check both exited zero.
   当 g++ 与 JNI headers 存在时，必须确认
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过，并对保留的
   同一份生成 translation unit 独立运行 `g++ -std=c++17 -fsyntax-only`。记录
   结果：59-method smoke 与独立语法检查均以零退出。
4. Inspect generated C++ for exact `Call[Int|Long|Object|Void]Method` and
   `CallNonvirtual[Int|Long|Object|Void]Method` families. Verify the interface
   argument count matches its descriptor and nonvirtual calls pass receiver,
   declaring class, method ID, then descriptor arguments.
   检查生成 C++ 是否使用精确的 `Call[Int|Long|Object|Void]Method` 与
   `CallNonvirtual[Int|Long|Object|Void]Method` family。确认接口实参数量与描述符
   一致，并确认 nonvirtual 调用依次传入 receiver、声明类、method ID 及描述符参数。
5. Verify a null interface receiver takes the shared exceptional exit and keeps
   the `NullPointerException` pending.
   确认 null 接口 receiver 进入共享异常出口，并保持
   `NullPointerException` pending。
6. During conflict resolution, retain fallback-before-mutation, the phase-9
   `jarray` cast, phase-10 field coverage, constructor-method exclusion, the
   `legacy` default, and all existing snippets.
   解决冲突时必须保留 mutation 前 fallback、phase-9 `jarray` 转换、phase-10
   字段覆盖、构造器方法体排除策略、`legacy` 默认值及全部现有 snippets。
