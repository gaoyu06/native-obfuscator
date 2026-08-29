# docs: Sol review of #137 eval port

## English

### (a) Scope

Independently review draft PR #137 at `c384cb5` against `master` at `76ebedd`.
The review checks CLI/API defaults, interpreter-first dispatch, the
pre-mutation fallback boundary, all Java/C++ opcode assignments, source-file
selection, frontend ownership, and omitted-vs-explicit `direct` output
identity. It adds the review note
`docs/reviews/ir-eval-port-sol.md`; no compiler source is changed.

### (b) Ship-ready?

**No.** The eval lowering remains an optional, deliberately limited
compiler/codegen path. The default remains `--ir-lower=direct`.

### (c) Review result

**Accept with nits.** No blocking compiler-correctness defect was found.
All 28 Java serializer and C++ evaluator opcode names and values agree;
`0x2b`/`0x2c` remain unused. The committed opcode-map regression checks only
selected constants, so a future data-driven full-map assertion would improve
drift detection.

### (d) Integration evidence

Independent dynamic reruns are pending and will be recorded before handoff.
The review already confirms that `legacy`, `cpp`, and `direct` remain the
defaults; all existing `NativeObfuscator.process` overloads remain available;
the five excluded frontend/emitter/test files are absent from the implementation
diff; benchmark/status files are unchanged; the #53 median remains `N/A`; and
no JDK support level or requirement-7 result is asserted.

## 中文

### (a) 范围

独立审查 draft PR #137：tip 为 `c384cb5`，`master` 基线为 `76ebedd`。
审查范围包括 CLI/API 默认值、interpreter 优先分派、mutation 前 fallback 边界、
Java/C++ 全部 opcode 编号、source file 选择、frontend 代码归属，以及省略
`--ir-lower` 与显式 `direct` 的输出一致性。新增审查文档
`docs/reviews/ir-eval-port-sol.md`，不修改编译器源码。

### (b) Ship-ready？

**No / 否。** eval lowering 仍是可选且能力范围有限的 compiler/codegen 路径；
默认值继续为 `--ir-lower=direct`。

### (c) 审查结论

**接受（附带小问题）。** 未发现阻塞性的编译器正确性缺陷。Java serializer 与
C++ evaluator 的 28 个 opcode 名称和值完全一致，`0x2b`/`0x2c` 继续未使用。
现有 opcode-map 回归测试只检查部分常量，后续可用数据驱动的全表断言加强漂移检测。

### (d) 集成证据

独立动态复跑正在进行，交付前会补充结果。静态审查已确认 `legacy`、`cpp` 和
`direct` 默认值保持不变；既有 `NativeObfuscator.process` overload 均保留；
五个排除的 frontend/emitter/test 文件均不在实现差异中；benchmark/status 文件
未修改；#53 median 仍为 `N/A`；没有新增 JDK 支持级别或 requirement-7 结论。
