# English

(a) Scope: Re-measure the checked-in IR admission fixtures after #190, using explicit `--codegen=ir` on master SHA `47e35fc01d8a324a55431dee1bd00759cfa7030e`. The joined totals are ClassicTest 108/108 IR, jdk17 82/82 IR, jdk21 47/47 IR, and jdk25 21/21 IR, with no fallback, constructor left in Java, or missing join. This updates the measurement report only; there is no compiler or runtime source change.

(b) Ship-ready? **No.**

(c) Gate: The executed gate is `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`, with its generated report in `docs/benchmarks/ir-leftover-inventory.md` and raw evidence under `/tmp/native-obfuscator-ir-leftover-inventory/`.

(d) Preconditions: Do not treat zero measured leftovers as coverage-complete or as a JDK support badge. Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

(a) 范围：在 #190 合入后，对 master SHA `47e35fc01d8a324a55431dee1bd00759cfa7030e` 上已检入的夹具显式使用 `--codegen=ir` 重新测量 IR 接纳情况。合并统计为 ClassicTest 108/108 IR、jdk17 82/82 IR、jdk21 47/47 IR、jdk25 21/21 IR；没有 fallback、留在 Java 中的构造器或缺失的关联项。本次只更新测量报告，不修改编译器或运行时源码。

(b) 可直接上线？**否。**

(c) 门禁：已执行的门禁命令为 `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`；生成的报告位于 `docs/benchmarks/ir-leftover-inventory.md`，原始证据位于 `/tmp/native-obfuscator-ir-leftover-inventory/`。

(d) 前置条件：不得把测得的零 leftover 视为覆盖完整或 JDK 支持标识。默认值保持不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
