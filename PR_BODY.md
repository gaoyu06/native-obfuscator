## English

### (a) Scope

Measurement-only remeasurement of the in-tree fixtures after #198. The helper measured compiler SHA `4214d7498c4b902d1dbf54f0bc14a3be16649b89` with explicit `--codegen=ir`; this change does not alter compiler/runtime source or defaults.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### (b) Ship-ready?

**No.**

### (c) Review and gate

Review the generated report's measured compiler SHA and joined totals above. This measurement-only change has no Gradle compiler-test gate and needs no stacked Fable/Sol review. The helper itself ran `./gradlew :obfuscator:shadowJar` successfully.

### (d) Preconditions

Remaining structural leftovers must stay reject-before-mutation. `--codegen` stays `legacy`. These fixture admission counts are not a JDK support badge and do not authorize a default flip.

## 中文

### (a) 范围

仅重新测量 #198 之后仓库内已有的 fixtures。helper 使用显式 `--codegen=ir` 测量了编译器 SHA `4214d7498c4b902d1dbf54f0bc14a3be16649b89`；本次变更不修改编译器/运行时源码或默认值。

| 语料 | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### (b) 可直接上线？

**否。**

### (c) 审查与门禁

请审查上述生成报告中的实测编译器 SHA 和 joined totals。本次仅测量的变更没有 Gradle 编译器测试门禁，也不需要叠加 Fable/Sol 审查。helper 自身已成功运行 `./gradlew :obfuscator:shadowJar`。

### (d) 前置条件

剩余结构性缺口必须继续在修改前拒绝（reject-before-mutation）。`--codegen` 保持 `legacy`。这些 fixture 接纳计数不是 JDK 支持徽章，也不授权翻转默认值。
