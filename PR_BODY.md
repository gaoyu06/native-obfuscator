# English

(a) Scope: Re-measure the checked-in IR admission fixtures after #180, using `--codegen=ir` on master SHA `c99b1582c47a69fe631180014a67bea4a97e2192`. This updates the inventory report only; there is no compiler or runtime change.

(b) Ship-ready? **No.** This is a measurement snapshot, not a release-readiness claim.

(c) Gate: The gate is the executed command `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac` and its generated report at `docs/benchmarks/ir-leftover-inventory.md`; this is not a stacked review.

(d) Preconditions: Do not treat zero measured leftovers as coverage-complete or as a JDK support badge. Do not change the defaults: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

(a) 范围：在 #180 合入后，对 master SHA `c99b1582c47a69fe631180014a67bea4a97e2192` 上已检入的夹具使用 `--codegen=ir` 重新测量 IR 接纳情况。本次只更新清单报告，不修改编译器或运行时。

(b) 可发布？**否。** 这是测量快照，不是发布就绪结论。

(c) 门禁：门禁是已执行的命令 `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac` 及其生成的 `docs/benchmarks/ir-leftover-inventory.md` 报告；这不是堆叠评审。

(d) 前置条件：不得把测得的零 leftover 视为覆盖完整或 JDK 支持标识。不得更改默认值：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
