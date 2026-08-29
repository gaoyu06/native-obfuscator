# IR phase 18 independent review / IR phase 18 独立审查

Reviewed tip: `b78d6d7c74b11c416f5703df89ad6b0c1532aec2`

Reviewed base: `5a6f6097524c1fe42cd82be2425f5e6736667688`

## Verdict / 结论

**Accept (docs-only review note), subject to the required focused rerun below.**
No compiler defect was found in the complete phase-18 diff. This review does
not claim JDK 17 runtime or product support.

**接受（仅增加审查文档），但仍须完成下述聚焦测试复跑。** 对 phase 18
完整差异的审查未发现编译器缺陷。本审查不声明 JDK 17 runtime 或产品支持。

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

Result: pending the independent rerun. Counts will be read from the generated
JUnit XML rather than copied from the author report.

结果：等待独立复跑。数量将直接读取生成的 JUnit XML，不复制作者报告。

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
  GCC/G++ rerun is required before the verdict is final. /
  仅堆叠在 `cursor/ir-compiler-phase18-6d81-a7ae`；保持 `legacy` 默认值，
  不合并或 rebase 到同级 JDK 17 runtime-fix 分支。结论最终生效前必须完成
  GCC/G++ 聚焦复跑。
