# IR compiler phase 9 / IR 编译器第九阶段

Preferred base / 首选基线:
`cursor/ir-compiler-phase8-6d81`
(`95eb5ffd2fc5a9515af65c1d15403e7c983c64a5`).

## Summary / 摘要

The optional Java bytecode → typed CFG IR → C++/JNI lowering path now supports
reference-returning method bodies, typed null values and branches, and
category-one discard. The default remains `legacy`. Detailed evidence is in
`docs/architecture/ir-phase9-status.md`.

可选的 Java 字节码 → typed CFG IR → C++/JNI lowering 路径现支持引用返回方法体、
typed null 值与分支，以及 category-one 丢弃。默认值仍为 `legacy`。详细证据见
`docs/architecture/ir-phase9-status.md`。

## (a) Change scope / 本次改动范围

- Admits `ARETURN` for object/array method descriptors through the existing
  `IrType.REFERENCE` / `jobject` carrier. Unprotected JNI failures return
  `nullptr` with the exception pending.
- Adds a typed `ACONST_NULL` IR value that emits `nullptr`.
- Adds dedicated structured reference conditions for `IFNULL` and `IFNONNULL`;
  they are not represented as integer compares.
- Admits `POP` only for one-slot/category-one values. `POP2`, category-two
  `POP`, and other category-two stack manipulation still fall back.
- Adds focused regressions for allocated-object return, null return, both null
  branch directions, category-one discard, category-two rejection, and
  fallback-before-mutation after newly admitted operations.
- Keeps constructor method bodies excluded, the default `legacy`, and all
  existing snippets.

- 通过现有 `IrType.REFERENCE` / `jobject` carrier 为对象/数组方法描述符接纳
  `ARETURN`；未受保护的 JNI 失败会在异常保持 pending 时返回 `nullptr`。
- 新增 typed `ACONST_NULL` IR 值并发射 `nullptr`。
- 为 `IFNULL` 与 `IFNONNULL` 新增专用 structured reference condition；不会将其
  表示为整数比较。
- 仅为单 slot/category-one 值接纳 `POP`；`POP2`、对 category-two 值的 `POP`
  及其他 category-two stack manipulation 仍会 fallback。
- 新增聚焦回归，覆盖分配对象返回、null 返回、null 分支两个方向、category-one
  丢弃、category-two 拒绝，以及新接纳操作之后的 mutation 前 fallback。
- 构造器方法体仍不处理，默认值仍为 `legacy`，全部现有 snippets 均保留。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 9 remains a partial, opt-in compiler slice. Unsupported bytecodes and
descriptors still fall back, including float/double, `MULTIANEWARRAY`,
non-`int` primitive arrays, reference fields, `INVOKEINTERFACE`, invokedynamic,
non-constructor `INVOKESPECIAL`, constructor method bodies, and category-two
stack manipulation. Focused unit and C++ syntax evidence does not replace
supported-platform native runtime-parity gates.

第九阶段仍是部分、可选的编译器增量。不支持的字节码与描述符仍会 fallback，包括
float/double、`MULTIANEWARRAY`、非 `int` primitive array、reference field、
`INVOKEINTERFACE`、invokedynamic、非构造器 `INVOKESPECIAL`、构造器方法体及
category-two stack manipulation。聚焦单测与 C++ 语法证据不能替代受支持平台上的
native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review reference return descriptor/carrier matching, JNI default returns on
exceptional exits, explicit reference-null branch typing and control-flow,
category-one `POP` validation, and fallback-before-mutation.

需要审查引用返回描述符/carrier 匹配、异常出口的 JNI 默认返回、显式 reference-null
分支 typing 与控制流、category-one `POP` 校验，以及 mutation 前 fallback。

## (d) Review preconditions / Review 前置条件

1. Compare and land on `cursor/ir-compiler-phase8-6d81` at `95eb5ffd…`, not
   `master` or a review-only branch, preserving the compiler stack order.
   必须基于 `cursor/ir-compiler-phase8-6d81` 的 `95eb5ffd…` 比较与落地，不得改用
   `master` 或仅审查分支，并保持编译器堆叠顺序。
2. Re-run the focused Gradle command with `CC=gcc CXX=g++ --rerun-tasks` and
   inspect the actual JUnit XML counts. The implementation checkpoint has not
   yet recorded final counts.
   使用 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦 Gradle 命令，并检查实际 JUnit
   XML 计数。当前 implementation checkpoint 尚未记录最终计数。
3. When g++ and JNI headers are present, require the g++ testcase to remain
   unskipped and independently run `g++ -std=c++17 -fsyntax-only` on the exact
   retained generated translation unit.
   当 g++ 与 JNI headers 存在时，必须确认 g++ 测试未跳过，并对保留的同一份生成
   translation unit 独立运行 `g++ -std=c++17 -fsyntax-only`。
4. Require supported-platform/JDK CI and native runtime-parity checks before
   any production decision.
   任何生产决策前都必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
5. During conflict resolution, retain exception routing, constructor-method
   exclusion, exact JNI carriers, explicit reference branch semantics,
   fallback-before-mutation, the `legacy` default, and existing snippets.
   解决冲突时必须保留异常路由、构造器方法体排除策略、精确 JNI carrier、显式引用
   分支语义、mutation 前 fallback、`legacy` 默认值及现有 snippets。
