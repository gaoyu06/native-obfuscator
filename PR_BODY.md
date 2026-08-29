# IR compiler phase 10 review / IR 编译器第十阶段审查

Reviewed head / 已审查版本:
`cursor/ir-compiler-phase10-6d81`
(`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`), stacked on
`cursor/ir-phase9-sol-review-6d81`
(`0e323da959d34f29b3c3cede206e48aa96a4559e`).

已审查 `cursor/ir-compiler-phase10-6d81`
（`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`），其基线为
`cursor/ir-phase9-sol-review-6d81`
（`0e323da959d34f29b3c3cede206e48aa96a4559e`）。

## Summary / 摘要

This independent review found no correctness defect in the phase-10 field
slice. Exact `I`, exact `J`, and object/array reference fields retain matching
IR types and JNI accessors; instance/static cache identities remain distinct;
null receivers and unsupported field sorts take the required exits. The review
changes documentation only. The default remains `legacy`.

本次独立审查未发现第十阶段字段增量中的正确性缺陷。精确 `I`、精确 `J` 以及
对象/数组引用字段保持匹配的 IR 类型和 JNI accessor；实例/静态缓存身份保持
独立；null receiver 与不支持的字段 sort 均走预期出口。本审查仅修改文档。
默认值仍为 `legacy`。

## (a) Review scope / 审查范围

- Descriptor-exact IR typing and JNI accessor selection for all four
  GET/PUT FIELD/STATIC opcodes.
- Instance versus static `CachedFieldInfo` identity and field-ID lookup.
- Null-receiver NPE creation and block exceptional exits.
- Frontend rejection of `Z`, `B`, `C`, `S`, `F`, and `D`.
- Fallback-before-mutation after newly admitted field operations.
- Preservation of the phase-9 ARETURN `jarray` cast, the `legacy` default, and
  constructor-body exclusion.

- 审查四种 GET/PUT FIELD/STATIC opcode 的描述符精确 IR typing 与 JNI
  accessor 选择。
- 审查实例与静态 `CachedFieldInfo` 身份及 field-ID lookup。
- 审查 null receiver 的 NPE 建立与 block 异常出口。
- 审查 frontend 对 `Z`、`B`、`C`、`S`、`F`、`D` 的拒绝。
- 审查新增字段操作之后的 mutation 前 fallback。
- 确认保留 phase-9 ARETURN `jarray` 转换、`legacy` 默认值及构造器方法体排除。

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

## (c) Review verdict / 审查结论

**PASS for the documented phase-10 scope / 在已记录的第十阶段范围内通过。**

No correctness bug was found, so no compiler code was changed. Source
inspection confirms exact `I`/`J`/reference typing, matching JNI families,
distinct instance/static field-cache identity, pending-NPE exceptional exits,
early rejection of the six unsupported primitive sorts, and preservation of
the phase-9 array-return fix.

未发现正确性 bug，因此未修改 compiler code。源码检查确认了精确的
`I`/`J`/引用 typing、匹配的 JNI family、彼此独立的实例/静态字段缓存身份、
pending-NPE 异常出口、对六种不支持 primitive sort 的前置拒绝，以及 phase-9
数组返回修复仍然存在。

## (d) Verification evidence / 验证证据

1. Static evidence and requirement-by-requirement findings are recorded in
   `docs/architecture/ir-phase10-review.md`.
   静态证据与逐项审查结论记录在
   `docs/architecture/ir-phase10-review.md`。
2. The required GCC/G++ Gradle re-run and actual JUnit XML counts will be
   recorded after the documentation-only review commit is pushed.
   文档审查 commit 推送后，将记录规定的 GCC/G++ Gradle 重跑结果及实际 JUnit
   XML 计数。
3. When g++ and JNI headers are present, the review requires
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` to be unskipped.
   当 g++ 与 JNI headers 存在时，审查要求
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 不得跳过。
