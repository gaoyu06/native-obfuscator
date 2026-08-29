## (a) Change scope / 改动范围

**English:** Measurement-only documentation and a reproducible helper. It inventories exact IR admission leftovers in the checked-in ClassicTest and JDK fixture sources. Compiler/runtime Java and C++, CLI defaults, and fixture sources are unchanged.

**中文：**仅包含测量文档和可复现 helper，用于清点仓库内 ClassicTest 与 JDK fixture 的精确 IR 接纳遗留项。未修改编译器/运行时 Java、C++、CLI 默认值或 fixture 源码。

## (b) Ship-ready? / 是否可直接发布？

**English:** No. This is an admission inventory, not native behavioral E2E, compatibility certification, or a JDK support badge. Explicit `--codegen=ir` was used; the default remains `legacy`.

**中文：**不能。这只是接纳清单，不是 native 行为 E2E、兼容性认证或 JDK 支持标志。测量显式使用 `--codegen=ir`；默认值仍为 `legacy`。

## (c) Review shape / Review 方式

**English:** Review this directly against current `master`; there is no stacked review dependency. Verify the exact method join, generated-method exclusions, logged first-failure reasons, and in-tree fixture set.

**中文：**请直接相对当前 `master` review；没有堆叠 review 依赖。请核对精确方法关联、生成方法排除、日志中的首个失败原因以及仓库内 fixture 集合。

## (d) Coverage limits / 覆盖范围限制

**English:** Do not treat these fixture counts as coverage complete. Static reject paths are listed separately from measured leftovers, and a zero count means only that this corpus did not exercise that path.

**中文：**不得将这些 fixture 计数视为覆盖完整。静态拒绝路径与实测遗留项分开列出；计数为零只表示本次语料未触发该路径。
