# Current-master JVM/legacy/IR measurement / 当前 master JVM、legacy 与 IR 实测

## English

- **(a) Scope:** Minimally extends the existing benchmark driver so one
  `:obfuscator:bench` invocation runs the unchanged kernels on the plain JVM,
  explicit `--codegen=legacy`, and explicit `--codegen=ir`. It preserves
  checksum preflight, records separate native stages and commands, saves
  transpiler logs, and classifies every measured method from IR markers or
  fallback records. The current-master 5-warmup/10-sample run is documented in
  `docs/benchmarks/results-ir-vs-legacy-master.md`.
- **(b) No.** This measurement is not ship-ready evidence, a release gate, or
  a portable performance result.
- **(c) Validation:** `python3 -m py_compile benchmarks/run.py` passed.
  `BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ ./gradlew
  :obfuscator:bench` passed both native builds, all three executions, and
  cross-mode checksum checks.
- **(d) Interpretation constraints:** Only `string-concat-hash` stayed fully
  on IR. `integer-loop` used legacy fallback; `recursion` used an IR entry
  method and a legacy-fallback helper. Fallback/mixed samples are not pure IR
  timings. Do not infer that native generally beats HotSpot, do not back-fill
  #53 (its eval median remains `N/A`), and do not mark the production goal
  complete.

## 中文

- **(a) 范围：** 对现有基准驱动做最小扩展，使一次 `:obfuscator:bench` 调用以
  plain JVM、显式 `--codegen=legacy` 和显式 `--codegen=ir` 三种方式运行未改动的
  相同内核。保留校验和预检，分别记录原生阶段与命令，保存转译日志，并根据 IR 标记或
  fallback 记录判定每个被测方法的实际路径。当前 master 上 5 次预热、10 次测量的结果
  记录于 `docs/benchmarks/results-ir-vs-legacy-master.md`。
- **(b) No / 否。** 本次测量不是可直接发布的证据、发布门槛或可移植性能结果。
- **(c) 验证：** `python3 -m py_compile benchmarks/run.py` 通过；
  `BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++ ./gradlew
  :obfuscator:bench` 通过两套原生构建、三种模式执行及跨模式校验和检查。
- **(d) 解读约束：** 只有 `string-concat-hash` 完全保持在 IR 路径；
  `integer-loop` 使用 legacy fallback；`recursion` 的入口方法走 IR、递归辅助方法走
  legacy fallback。fallback 或混合路径的样本不是纯 IR 时间。不得据此宣称原生代码
  通常快于 HotSpot；不得回填 #53（其 eval 中位数仍为 `N/A`）；不得标记生产目标已经
  完成。
