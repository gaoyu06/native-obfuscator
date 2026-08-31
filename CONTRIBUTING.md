# Contributing / 参与贡献

## English

Thanks for contributing. This repository is strict about **status wording** —
read these two pages before changing anything:

- [`docs/architecture/current-goal.md`](docs/architecture/current-goal.md) —
  the active goal: move all method-body codegen onto the typed CFG IR, then
  delete the legacy snippet path. Not done; `--codegen` still defaults to
  `legacy`.
- [`docs/architecture/project-status.md`](docs/architecture/project-status.md) —
  what is true on `master` and which claims are **not** allowed.

### Build and test

```text
./gradlew assemble          # skip tests
./gradlew build             # full suite (some cases need krak2 on PATH)
```

Focused IR suite (the usual gate for IR work; recorded runs used gcc/g++):

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Changes are accepted on **executed tests** (focused tests plus
compile-and-run harnesses), not on code reading alone.

### Do not overclaim

- Do not write "supports JDK 17/21/25" from admission counts or small fixture
  sets. Java 8 remains the only version this project has ever called fully
  supported.
- Do not invent benchmark numbers or claim a general native speedup versus
  HotSpot. The latest recorded run is
  [`docs/benchmarks/results-ir-vs-legacy-phase19.md`](docs/benchmarks/results-ir-vs-legacy-phase19.md).
- Do not flip the `--codegen` default or call the legacy path deleted — both
  are explicit future steps in the current goal.
- Do not present the interpreter, the eval lowering, or the C++ SDK as
  shipped products; they are default-off or packaging details.

## 中文

感谢参与。本仓库对**状态措辞**非常严格——改任何东西之前先读：

- [`docs/architecture/current-goal.md`](docs/architecture/current-goal.md)——
  现行目标：把所有方法体代码生成迁到 typed CFG IR，然后删除 legacy 路径。
  该目标尚未完成，`--codegen` 默认仍是 `legacy`。
- [`docs/architecture/project-status.md`](docs/architecture/project-status.md)——
  master 上什么是真的、哪些说法**不允许**出现。

### 构建与测试

构建：`./gradlew assemble`（跳过测试）；完整套件：`./gradlew build`
（部分用例需要 `krak2` 在 `PATH` 上）。IR 工作的常规门禁是上面的聚焦测试
（`IrCompilerTest` + `CodegenModeTest`，配 `CC=gcc CXX=g++`），验收以
**真实跑过的测试**为准，而不是只读代码。

### 不要夸大

- 不要凭接纳率或小语料写“已支持 JDK 17/21/25”；Java 8 仍是唯一称过完整支持的版本。
- 不要编造基准数字，也不要声称对 HotSpot 的普适加速。
- 不要翻转 `--codegen` 默认值，也不要说 legacy 已删除——两者都是现行目标里明确的后续步骤。
- 解释器、eval 降级和 C++ SDK 都不是已发布的产品，不要如此描述。

## Issues

Open an issue on this repository, or contact the original author at
[re146.dev](https://re146.dev). License: [GPL-3.0](LICENSE).
