# IR phase 10 — Fable review / IR 编译器第十阶段 —— Fable 审阅

Fable design review of the Sol implementation of phase 10 (typed instance and
static field access for exact `I`, exact `J`, and object/array reference
descriptors in the Java bytecode → typed CFG IR → C++/JNI compiler).

对第十阶段 Sol 实现的 Fable 设计审阅（Java 字节码 → typed CFG IR → C++/JNI
编译路径中，实例与静态字段访问对精确 `I`、精确 `J` 及对象/数组引用描述符的支持）。

Review base / 审阅基线:
`cursor/ir-phase9-sol-review-6d81` at
`0e323da959d34f29b3c3cede206e48aa96a4559e` (reviewed phase-9 tip with the
array-return `jarray` carrier fix). Phase-10 tip reviewed:
`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`.
必须基于 `cursor/ir-phase9-sol-review-6d81` 的 `0e323da…` 比较，而非 `master`。

Full write-up / 完整报告: `docs/architecture/ir-phase10-fable-review.md`.

**Verdict / 结论: accept-with-nits（接受，有小瑕疵）.**
No compiler code changed on this review branch — no correctness blocker was
found. / 本审阅分支未改动任何编译器代码：未发现正确性阻塞项。

## (a) What was reviewed / 审阅范围

- `GETFIELD`/`PUTFIELD`/`GETSTATIC`/`PUTSTATIC` for exact `I`, exact `J`, and
  object/array descriptors, and the descriptor→`IrType` map
  (`I→I32`, `J→I64`, object/array→`REFERENCE`) consulted at admission, at the
  abstract stack effect, at lowering, and at `IrNodes` node construction.
- Matching JNI accessor selection: `Get/Set[Static]IntField`,
  `Get/Set[Static]LongField`, `Get/Set[Static]ObjectField` (arrays use the
  `Object` family).
- Null instance receiver → block exceptional exit with `NullPointerException`
  left pending (no `ExceptionClear`).
- The six other primitive field sorts (`Z`,`B`,`C`,`S`,`F`,`D`) still rejected.
- Reject-before-mutation for unsupported descriptors.
- Phase-9 array-return `jarray` carrier cast retained (and interoperating with a
  phase-10 array field read).
- Default codegen mode stays `legacy`.

- `GETFIELD`/`PUTFIELD`/`GETSTATIC`/`PUTSTATIC` 对精确 `I`、精确 `J` 及对象/数组
  描述符的支持，以及描述符→`IrType` 映射（`I→I32`、`J→I64`、对象/数组→
  `REFERENCE`）在准入、抽象栈效果、lowering 与 `IrNodes` 节点构造四处的一致性。
- JNI accessor 选择：`Get/Set[Static]{Int,Long,Object}Field`（数组走 `Object`）。
- 实例接收者为 null → 走块异常出口且 `NullPointerException` 保持 pending
  （无 `ExceptionClear`）。
- 其余六种 primitive 字段 sort（`Z`,`B`,`C`,`S`,`F`,`D`）仍被拒。
- 不支持描述符的变更前回退。
- phase-9 数组返回 `jarray` carrier 转换保留（并与 phase-10 数组字段读取协同）。
- 默认代码生成模式仍为 `legacy`。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 10 remains a partial, opt-in compiler slice. Unsupported descriptors and
bytecodes still fall back per method, including the six other primitive field
sorts, float/double operations, and the other constructs already documented as
out of scope. Focused unit tests plus g++ syntax evidence do not replace
runtime-parity gates on supported platforms. This review makes no production
claim; it only reports code-generation correctness for the field slice.

第十阶段仍是部分、可选的编译器增量。不支持的描述符与字节码仍按方法回退，包括其余
六种 primitive 字段 sort、float/double 操作及其它已记录为范围外的构造。聚焦单测与
g++ 语法证据不能替代受支持平台上的运行时等价性门禁。本审阅不作上线结论，仅报告字段
增量的代码生成正确性。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

A reviewer must confirm descriptor-exact IR typing and JNI accessor selection,
the instance-versus-static field cache identity, null-receiver exception routing
with the NPE left pending, reject-before-mutation, and that the stacked phase-9
array-return carrier fix remains present.

Review 必须确认描述符精确的 IR typing 与 JNI accessor 选择、实例与静态访问的字段
缓存身份、null 接收者异常路由（NPE 保持 pending）、变更前回退，以及堆叠基线中的
phase-9 数组返回 carrier 修复是否保留。

## (d) Review evidence / 审阅证据

- Compared against `cursor/ir-phase9-sol-review-6d81` at `0e323da…`, not
  `master`. / 基于 `0e323da…` 比较，而非 `master`。
- Re-ran the focused Gradle command with `CC=gcc CXX=g++` and read the JUnit XML:
  `IrCompilerTest` **47**, `CodegenModeTest` **2**, total **49**; **0** skipped,
  **0** failures, **0** errors. / 以 `CC=gcc CXX=g++` 重跑聚焦命令并核对 JUnit
  XML：47 + 2 = 49，跳过/失败/错误均为 0。
- `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran (not skipped,
  `time="0.282"`, empty g++ output); independently recompiled the exact
  generated translation unit (`g++ -std=c++17 -fsyntax-only`, 98 090 bytes,
  exit 0). / 该 g++ 冒烟真实运行（未跳过），并独立重编生成翻译单元（退出 0）。
- Read the emitted C++ from the g++-accepted file: `GetLongField`/`SetLongField`,
  `GetObjectField`/`SetObjectField`, their `Static` counterparts, the
  null-receiver `throw_re` exits with no `ExceptionClear`, and the `[I` field
  read via `GetObjectField` returned through `return (jarray) v…`. / 从被 g++
  接受的文件中阅读生成 C++：Long/Object 及其 Static accessor、无 `ExceptionClear`
  的 null-receiver 出口，以及经 `GetObjectField` 读取 `[I` 并以 `(jarray)` 返回。

Nits (non-blocking) / 小瑕疵（不阻塞）: the descriptor→carrier map is duplicated
across `AsmToIr.fieldType`, `IrNodes.fieldType`, and `IrCppEmitter.fieldCarrier`
(agree today, must stay in sync); `fieldCarrier` lacks the malformed-descriptor
guard its sibling has; field-id lookup is emitted before the receiver null check
(JVM-invisible); plus the carried-forward prior-phase nits. / 描述符→carrier
映射重复于三处（今日一致，须同步）；`fieldCarrier` 缺少畸形描述符保护；字段 id
查找发射于空检查之前（JVM 不可见）；以及沿用既往阶段的小瑕疵。
