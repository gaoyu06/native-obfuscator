# docs: re-measure JVM/legacy/IR bench after phase 19

## English

### (a) Scope

Re-measures the current-master three-mode benchmark (plain JVM,
`--codegen=legacy`, `--codegen=ir`) on `origin/master`
`76ebeddb005e01033523384275c8c0c1641ada81`, the first master tip that includes
IR phase 19 (`LAND`/`LOR`/`LXOR`, `LSHL`/`LSHR`/`LUSHR`). The previous run in
`docs/benchmarks/results-ir-vs-legacy-master.md` was recorded on pre-phase-19
`e997d71`, where `integer-loop` IR mode was a legacy fallback (opcode 125
`LUSHR`) and `recursion` was mixed (`recurse` fell back on opcode 131 `LXOR`).

Changes are docs-only:

- new `docs/benchmarks/results-ir-vs-legacy-phase19.md` with commit SHA,
  exact commands, environment versions, per-kernel median/mean for all three
  modes, every raw sample and checksum, an IR path audit with evidence, and a
  "must not be read as" section;
- `docs/benchmarks/README.md` now points at the phase-19 file as the latest
  three-mode run and keeps the old file as the pre-phase-19 record.

All numbers were freshly measured in this run
(`BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ ./gradlew :obfuscator:bench`,
status PASS, all cross-mode checksums matched). Nothing was copied or
back-filled. No compiler, IR, interpreter, SDK, CLI-flag, or harness source was
changed; the `--codegen` default stays `legacy`.

Key measured outcome: all four measured kernel methods now emit
`// IR codegen:` markers and `transpile-ir.log` contains zero fallback lines,
so for the first time all three kernels are pure IR timings.
`IntegerLoopKernel.run(I)J` and `RecursionKernel.recurse(IJ)J` admitted as
phase-19 admission predicted.

### (b) Ship-ready?

**No.** This is a single-VM, single-process diagnostic run. It is not a
release gate, not a portable speedup claim, and does not complete the
production goal. Requirement 7 (resist unaided Sol-class recovery) is unmet.

### (c) Review required?

**Yes.** Reviewers should specifically check:

- that no fallback or mixed row is presented as an IR timing (this run had
  none — verify the audit evidence: markers under
  `build/benchmarks/work/ir/transpiled`, empty fallback log);
- that the #53 eval median was **not** back-filled — it stays `N/A` in
  `docs/benchmarks/results-ir-eval-lower.md`, which this PR does not touch;
- that no general "native beats HotSpot" claim was introduced.

### (d) Preconditions

- Cite only this run's measured file
  (`docs/benchmarks/results-ir-vs-legacy-phase19.md`) for post-phase-19
  numbers; the old master file is the pre-phase-19 record only.
- Keep `--codegen` default at `legacy`.
- Do not mark the production goal complete on the basis of this run.

---

## 中文

### (a) 范围

在 `origin/master` `76ebeddb005e01033523384275c8c0c1641ada81`（首个包含 IR
第 19 阶段 `LAND`/`LOR`/`LXOR`、`LSHL`/`LSHR`/`LUSHR` 的 master 提交）上重新
测量三模式基准（纯 JVM、`--codegen=legacy`、`--codegen=ir`）。之前的
`docs/benchmarks/results-ir-vs-legacy-master.md` 记录于第 19 阶段之前的
`e997d71`：当时 `integer-loop` 的 IR 模式实际是 legacy 回退（操作码 125
`LUSHR`），`recursion` 是混合路径（`recurse` 因操作码 131 `LXOR` 回退）。

本 PR 仅改文档：

- 新增 `docs/benchmarks/results-ir-vs-legacy-phase19.md`，包含被测提交
  SHA、精确命令、环境版本、三种模式的每内核中位数/均值、全部原始样本与
  校验和、带证据的 IR 路径审计表，以及"不得解读为"章节；
- `docs/benchmarks/README.md` 指向新的第 19 阶段结果文件作为最新三模式
  运行，旧文件保留为第 19 阶段之前的记录。

所有数字均为本次实测
（`BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ ./gradlew :obfuscator:bench`，
状态 PASS，三模式校验和全部一致），没有任何复制或回填。未改动编译器、
IR、解释器、SDK、CLI 标志或基准脚本源码；`--codegen` 默认值仍为 `legacy`。

关键实测结论：全部四个被测内核方法均生成 `// IR codegen:` 标记，
`transpile-ir.log` 中没有任何回退日志，因此三个内核首次全部是纯 IR 计时。
`IntegerLoopKernel.run(I)J` 与 `RecursionKernel.recurse(IJ)J` 如第 19 阶段
准入评估预测的那样成功准入。

### (b) 可以发布吗？

**否。** 这是单虚拟机、单进程的诊断性运行，不是发布门槛，不是可移植的
加速结论，也不代表生产目标完成。需求 7（抵御无辅助的 Sol 级恢复）仍未
满足。

### (c) 需要评审吗？

**是。** 评审者应重点检查：

- 没有把回退或混合路径的行标注为 IR 计时（本次运行没有回退——请核对审计
  证据：`build/benchmarks/work/ir/transpiled` 下的标记、空的回退日志）；
- **没有**回填 #53 的 eval 中位数——它在
  `docs/benchmarks/results-ir-eval-lower.md` 中保持 `N/A`，本 PR 未触碰
  该文件；
- 没有引入"原生普遍快于 HotSpot"之类的泛化结论。

### (d) 前提条件

- 引用第 19 阶段之后的数字时，只引用本次实测文件
  （`docs/benchmarks/results-ir-vs-legacy-phase19.md`）；旧的 master 文件
  仅作为第 19 阶段之前的记录。
- `--codegen` 默认值保持 `legacy`。
- 不得依据本次运行宣布生产目标完成。
