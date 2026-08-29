# IR phase 18 independent review / IR phase 18 独立审查

Reviewed tip: `b78d6d7c74b11c416f5703df89ad6b0c1532aec2`

Reviewed base: `5a6f6097524c1fe42cd82be2425f5e6736667688`

## Verdict / 结论

**Accept (docs-only review note).** No compiler defect was found in the
complete phase-18 diff, and the required focused rerun passed. This review
does not claim JDK 17 runtime or product support.

**接受（仅增加审查文档）。** 对 phase 18 完整差异的审查未发现编译器缺陷，
且要求的聚焦测试复跑已通过。本审查不声明 JDK 17 runtime 或产品支持。

## Review evidence / 审查证据

- The frontend admits all eight valid primitive `NEWARRAY` atypes and maps
  every matching load/store opcode to the correct typed IR carrier. `[Z` and
  `[B` descriptors select the distinct Boolean and Byte JNI families; an
  imprecise carrier retains the existing runtime discriminator.
- `NEWARRAY` and every supplied `MULTIANEWARRAY` dimension test for a negative
  count before allocation and preserve the pending exception through the
  block's exceptional edge.
- `MULTIANEWARRAY` pops dimension operands from the JVM stack in reverse and
  stores them outermost-to-innermost. The emitter passes total and supplied
  dimension counts separately to the retained recursive helpers. Their
  `required_count == 1` stop preserves null inner arrays for partial dimensions.
- Reference descriptors originate at parameters, allocations, casts, field
  reads, invoke returns, constants, and reference-array loads, then propagate
  through reference phis before JNI family selection.
- Long/double array accesses remain one category-2 IR value with two JVM slots;
  stack-phi slot numbering and the region temporary types remain consistent.
- `Main`, `NativeObfuscator`, and `MethodProcessor` retain explicit `legacy`
  defaults. Unsupported methods are still validated before shell mutation and
  retain per-method legacy fallback. The phase-18 ancestry is exactly the
  phase-17 tip and does not include the sibling JDK 17 runtime-fix stack.

- frontend 接受八种合法 primitive `NEWARRAY` atype，并将对应 load/store
  opcode 映射到正确的 typed IR carrier。`[Z` 与 `[B` descriptor 分别选择
  Boolean 与 Byte JNI family；不精确 carrier 继续使用既有 runtime
  discriminator。
- `NEWARRAY` 及 `MULTIANEWARRAY` 的每个已提供维度均在分配前检查负数，
  pending exception 通过所在 block 的 exceptional edge 保留。
- `MULTIANEWARRAY` 逆序弹出 JVM stack 操作数后按最外层到最内层保存；
  emitter 分别向既有递归 helper 传入总维数和已提供维数。
  `required_count == 1` 的停止条件保证部分维度分配时内层保持 null。
- Reference descriptor 来自参数、分配、cast、field 读取、invoke 返回、
  常量及 reference-array load，并在选择 JNI family 前通过 reference phi
  传播。
- long/double array access 仍以一个占两个 JVM slot 的 category-2 IR value
  表示；stack-phi slot 编号和 region 临时变量类型保持一致。
- `Main`、`NativeObfuscator` 与 `MethodProcessor` 仍显式默认使用
  `legacy`。不支持的方法继续在 shell mutation 前完成验证并按方法回退到
  legacy。phase-18 ancestry 精确基于 phase-17 tip，不含同级 JDK 17
  runtime-fix 分支。

## Focused verification / 聚焦验证

Required command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --console=plain
```

Independent result on 2026-08-29: `BUILD SUCCESSFUL`; all seven Gradle tasks
were executed.

Counts read from the generated JUnit XML:

```text
IrCompilerTest: tests=88, skipped=0, failures=0, errors=0
CodegenModeTest: tests=4, skipped=0, failures=0, errors=0
Total: 92 tests, 0 skipped, 0 failures, 0 errors
```

The assertion-based g++ syntax gate ran (0.421 s). Its generated translation
unit contained exactly 151 `JNICALL` functions. A separate
`g++ -std=c++17 -fsyntax-only` invocation against that unit also exited zero
with empty diagnostics.

2026-08-29 独立复跑结果为 `BUILD SUCCESSFUL`，七个 Gradle task 均实际执行。
生成的 JUnit XML 显示 `IrCompilerTest` 88 个、`CodegenModeTest` 4 个，
合计 92 个测试，0 skipped、0 failures、0 errors。断言式 g++ 语法门槛实际
运行（0.421 s）；生成单元含恰好 151 个 `JNICALL` function。另行对该单元
执行 `g++ -std=c++17 -fsyntax-only` 同样以 0 退出且诊断为空。

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Accept phase 18 as an opt-in typed-CFG IR increment:
  all primitive `NEWARRAY` atypes with matching primitive loads/stores, and
  rectangular primitive/reference `MULTIANEWARRAY`. /
  接受 phase 18 作为 opt-in typed-CFG IR 增量：全部 primitive `NEWARRAY`
  atype 及匹配的 primitive load/store，以及矩形 primitive/reference
  `MULTIANEWARRAY`。
- **(b) Ship-ready? / 可直接发布？:** **No.** This remains opt-in IR work and
  is not a broad runtime or JDK support gate. /
  **否。** 这仍是 opt-in IR 工作，不构成广泛 runtime 或 JDK 支持门槛。
- **(c) Review result / 审查结果:** No compiler change requested after checking
  JNI family selection, negative-size behavior, dimension order, partial
  dimensions, phi descriptor propagation, category-2 values, defaults, and
  fallback boundaries. /
  审查 JNI family 选择、负尺寸行为、维度顺序、部分维度、phi descriptor
  传播、category-2 value、默认值及 fallback 边界后，不要求编译器改动。
- **(d) Integration / 集成:** Stack only on
  `cursor/ir-compiler-phase18-6d81-a7ae`; preserve `legacy` as the default and
  do not merge or rebase onto the sibling JDK 17 runtime-fix stack. The focused
  GCC/G++ rerun completed with 92/92 tests passing. /
  仅堆叠在 `cursor/ir-compiler-phase18-6d81-a7ae`；保持 `legacy` 默认值，
  不合并或 rebase 到同级 JDK 17 runtime-fix 分支。GCC/G++ 聚焦复跑已完成，
  92/92 个测试通过。
