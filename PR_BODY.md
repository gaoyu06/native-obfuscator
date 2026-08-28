# IR phase 7 compiler review / IR 编译器第七阶段审查

Preferred base / 首选基线:
`cursor/ir-phase6-sol-review-6d81`
(`ac01e555aaa0109f61e98472dedd20f481643cf7`).

Review verdict / 审查结论: **Accept / 接受**.

## (a) Scope / 范围

- This branch is the compiler review of
  [PR #54](https://github.com/gaoyu06/native-obfuscator/pull/54), stacked on
  [PR #51](https://github.com/gaoyu06/native-obfuscator/pull/51). Every changed
  IR implementation file and the added tests were read.
- `CHECKCAST` and `INSTANCEOF` were checked from typed frontend nodes through
  structured C++ emission. Null cast succeeds unchanged, null `INSTANCEOF`
  yields 0, cast mismatch raises `ClassCastException`, and protected failures
  enter the shared `IR_CATCH` dispatcher.
- Ordinary and array targets were checked separately. Array descriptors such as
  `[Ljava/lang/String;` remain unchanged and use `JNIEnv::FindClass`.
- The two-slot `I64` slice was checked across parameters, locals, stack phis,
  `LADD`/`LSUB`/`LMUL`, conversions, and `LRETURN`. Emitted carriers are
  `jlong`; wrapping arithmetic uses standard `uint64_t`.
- The mixed `LDIV` regression proves fallback before method, output, metadata,
  or cache mutation. The CLI and API defaults remain `legacy`.
- No correctness blocker was found. Detailed evidence is in
  `docs/architecture/ir-phase7-review.md`.

- 本分支是对
  [PR #54](https://github.com/gaoyu06/native-obfuscator/pull/54) 的编译器审查，
  基于 [PR #51](https://github.com/gaoyu06/native-obfuscator/pull/51)。已逐一阅读全部
  IR 改动文件及新增测试。
- 已从 typed frontend 节点一直核验到 `CHECKCAST` / `INSTANCEOF` 的结构化 C++
  发射：空引用 cast 原样成功，空引用 `INSTANCEOF` 得到 0，cast 不匹配抛出
  `ClassCastException`，受保护失败进入共享 `IR_CATCH` dispatcher。
- 已分别核验普通目标和数组目标；`[Ljava/lang/String;` 等数组描述符保持原样并使用
  `JNIEnv::FindClass`。
- 已核验双 slot `I64` 在参数、局部变量、stack phi、`LADD`/`LSUB`/`LMUL`、
  转换及 `LRETURN` 上的完整增量。发射 carrier 为 `jlong`，回绕算术使用标准
  `uint64_t`。
- 混合 `LDIV` 回归证明 fallback 先于方法、输出、元数据或缓存 mutation；CLI 与
  API 默认值仍为 `legacy`。
- 未发现正确性阻断项。详细证据见 `docs/architecture/ir-phase7-review.md`。

## (b) Ship-ready? / 可直接发布？

**No / 否。**

The reviewed phase-7 delta has no compiler correctness blocker, but this
focused review provides unit and C++ syntax evidence rather than the full
supported-platform native runtime-parity gate. The stacked base also still
requires normal human disposition. Keep `legacy` as the default.

已审查的 phase-7 增量不存在编译器正确性阻断项，但本次聚焦审查提供的是单元测试与
C++ 语法证据，不能替代全部受支持平台上的 native 运行时等价性门禁；堆叠基线也仍需
人工正常处理。默认值应继续保持为 `legacy`。

## (c) This IS the review / 本次提交即为审查

**Yes / 是。**

This document and `docs/architecture/ir-phase7-review.md` constitute the
requested compiler review; they are not a request for another substitute
review. The verdict is accept, with no blocker or nit found in scope.

本文件与 `docs/architecture/ir-phase7-review.md` 即为所要求的编译器审查，并非再次
请求替代审查。结论为“接受”，审查范围内未发现阻断项或附带问题。

## (d) Human preconditions / 人工前置条件

1. Compare and land this work on `cursor/ir-phase6-sol-review-6d81`, not
   `master`, preserving the stack order.
   必须基于 `cursor/ir-phase6-sol-review-6d81` 比较与落地，不得改用 `master`，
   并保持堆叠顺序。
2. Require final supported-platform/JDK CI and native runtime-parity checks.
   最终提交必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
3. Re-run the focused command and inspect JUnit XML. Recorded final result:
   `IrCompilerTest` 33 plus `CodegenModeTest` 2, total 35; zero skipped,
   failures, or errors.
   重跑聚焦命令并检查 JUnit XML。最终记录为 33 + 2，共 35 个测试；跳过、失败、
   错误均为零。
4. Confirm the g++ testcase remains unskipped when g++ and JNI headers are
   present, and independently syntax-check the retained 29-method translation
   unit.
   当 g++ 与 JNI 头文件存在时，确认该测试未跳过，并独立语法检查保留的 29-method
   翻译单元。
5. During conflict resolution, retain type-test null semantics, array-target
   `FindClass`, protected exception routing, wide-slot invariants, unsigned
   wrapping arithmetic, fallback-before-mutation coverage, and the `legacy`
   default.
   解决冲突时必须保留类型测试空引用语义、数组目标 `FindClass`、受保护异常路由、
   宽 slot 不变量、无符号回绕算术、mutation 前 fallback 覆盖及 `legacy` 默认值。
