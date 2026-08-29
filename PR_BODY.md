# IR compiler phase 10 / IR 编译器第十阶段

Preferred base / 首选基线:
`cursor/ir-phase9-sol-review-6d81`
(`0e323da959d34f29b3c3cede206e48aa96a4559e`).
This is the reviewed phase-9 tip containing the array-return `jarray` carrier
fix. / 这是包含数组返回 `jarray` carrier 修复的 phase-9 已审查 tip。

## Summary / 摘要

Phase 10 adds typed instance and static field access to the optional Java
bytecode → typed CFG IR → C++/JNI compiler. Exact `I`, exact `J`, and object or
array reference fields now use their matching JNI carriers and accessors. The
default remains `legacy`.

第十阶段为可选的 Java 字节码 → typed CFG IR → C++/JNI 编译路径增加 typed 实例与
静态字段访问。精确 `I`、精确 `J` 以及对象或数组引用字段分别使用匹配的 JNI
carrier 与 accessor。默认值仍为 `legacy`。

## (a) Change scope / 本次改动范围

- Admits `GETFIELD`, `PUTFIELD`, `GETSTATIC`, and `PUTSTATIC` for exact `I`,
  exact `J`, and object/array field descriptors.
- Maps `I` to `IrType.I32` / `jint`, `J` to `IrType.I64` / `jlong`, and
  object/array descriptors to `IrType.REFERENCE` / `jobject`; there is no
  implicit integer widening for field access.
- Selects `Get/Set[Static]IntField`, `Get/Set[Static]LongField`, or
  `Get/Set[Static]ObjectField` from the descriptor while retaining the existing
  `CachedFieldInfo`, `cfields`, `GetFieldID`, and `GetStaticFieldID` paths.
- Routes a null instance receiver through the block exceptional exit with
  `NullPointerException` pending.
- Rejects field sorts `Z`, `B`, `C`, `S`, `F`, and `D` during frontend
  admission. They remain per-method fallback cases.
- Adds instance/static get+put round-trips for `I`, `J`, object references, and
  `[I` fields; null-receiver and fallback-before-mutation regressions; and all
  new methods to the retained g++ translation unit.
- Preserves the phase-9 array-return regression and `jarray` boundary cast,
  constructor-method exclusion, `legacy` default, and every existing snippet.

- 为精确 `I`、精确 `J` 及对象/数组字段描述符接纳 `GETFIELD`、`PUTFIELD`、
  `GETSTATIC` 与 `PUTSTATIC`。
- 将 `I` 映射到 `IrType.I32` / `jint`，将 `J` 映射到 `IrType.I64` /
  `jlong`，将对象/数组描述符映射到 `IrType.REFERENCE` / `jobject`；字段访问
  不进行隐式整数拓宽。
- 根据描述符选择 `Get/Set[Static]IntField`、`Get/Set[Static]LongField` 或
  `Get/Set[Static]ObjectField`，并保留现有 `CachedFieldInfo`、`cfields`、
  `GetFieldID` 与 `GetStaticFieldID` 路径。
- 实例字段接收者为 null 时，在 `NullPointerException` 保持 pending 的情况下走
  当前 block 的异常出口。
- 在 frontend 准入阶段拒绝 `Z`、`B`、`C`、`S`、`F` 与 `D` 字段 sort；这些
  情况继续按方法 fallback。
- 新增实例/静态 `I`、`J`、对象引用及 `[I` 字段的 get+put round-trip，
  null-receiver 与 mutation 前 fallback 回归，并将全部新方法加入保留的 g++
  translation unit。
- 保留 phase-9 数组返回回归与 `jarray` 边界转换、构造器方法体排除、
  `legacy` 默认值及全部现有 snippets。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 10 remains a partial, opt-in compiler slice. Unsupported bytecodes and
descriptors still fall back, including the six other primitive field sorts,
float/double operations, `MULTIANEWARRAY`, non-`int` primitive array
operations, `INVOKEINTERFACE`, invokedynamic, non-constructor
`INVOKESPECIAL`, constructor method bodies, and category-two stack
manipulation. Focused unit and C++ syntax evidence does not replace
supported-platform native runtime-parity gates.

第十阶段仍是部分、可选的编译器增量。不支持的字节码与描述符仍会 fallback，包括
其余六种 primitive 字段 sort、float/double 操作、`MULTIANEWARRAY`、非 `int`
primitive array 操作、`INVOKEINTERFACE`、invokedynamic、非构造器
`INVOKESPECIAL`、构造器方法体及 category-two stack manipulation。聚焦单测与
C++ 语法证据不能替代受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review must confirm descriptor-exact IR typing and JNI accessor selection,
field cache identity for instance versus static access, null-receiver
exception routing, and fallback-before-mutation. The stacked phase-9
array-return carrier fix must remain present.

Review 必须确认描述符精确的 IR typing 与 JNI accessor 选择、实例和静态访问的
字段缓存身份、null receiver 异常路由，以及 mutation 前 fallback。堆叠基线中的
phase-9 数组返回 carrier 修复必须保留。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-phase9-sol-review-6d81` at `0e323da…`, not
   `master`, the unfixed phase-9 branch, or the Fable review branch.
   必须基于 `cursor/ir-phase9-sol-review-6d81` 的 `0e323da…` 比较，不得改用
   `master`、未修复的 phase-9 分支或 Fable review 分支。
2. Re-run the focused Gradle command with `CC=gcc CXX=g++ --rerun-tasks` and
   inspect the actual JUnit XML counts. Recorded result: `IrCompilerTest` 47
   plus `CodegenModeTest` 2, total 49; zero skipped, failures, or errors.
   使用 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦 Gradle 命令，并检查实际 JUnit
   XML 计数。记录结果为 47 + 2，共 49 个测试；跳过、失败、错误均为零。
3. With g++ and JNI headers present, require
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` to remain
   unskipped and independently run `g++ -std=c++17 -fsyntax-only` on the exact
   retained generated translation unit. Recorded result: the 50-method smoke
   and independent syntax check both exited zero.
   当 g++ 与 JNI headers 存在时，必须确认
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过，并对保留的
   同一份生成 translation unit 独立运行 `g++ -std=c++17 -fsyntax-only`。记录
   结果：50-method smoke 与独立语法检查均以零退出。
4. Inspect generated C++ for exact `Int`, `Long`, and `Object` instance/static
   accessor families and verify null get/put exits keep the NPE pending.
   检查生成 C++ 是否使用精确的 `Int`、`Long` 与 `Object` 实例/静态 accessor
   family，并确认 null get/put 异常出口保持 NPE pending。
5. During conflict resolution, retain fallback-before-mutation, the phase-9
   `jarray` cast, constructor-method exclusion, the `legacy` default, and all
   existing snippets.
   解决冲突时必须保留 mutation 前 fallback、phase-9 `jarray` 转换、构造器方法体
   排除策略、`legacy` 默认值及全部现有 snippets。
