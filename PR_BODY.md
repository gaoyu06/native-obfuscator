# Packaging: emit `Enable-Native-Access: ALL-UNNAMED` for JEP 472 forward-compat

## English

### (a) What & why

Generated JARs load native code through `native0.Loader` / `LoaderUnpack` /
`LoaderPlain`, which call `System.load` / `System.loadLibrary`. Under
[JEP 472](https://openjdk.org/jeps/472) those are *restricted* methods: JDK 24+
warns by default on an unenabled call, and a future release may turn the warning
into an error. The landed JDK 25 IR E2E
(`docs/benchmarks/ir-jdk25-e2e-corpus.md`, `5ac6ec5`) recorded that four-line
warning on every transformed run.

This change is **packaging + docs only**. It gives output JARs a forward-compat
story so a `java -jar` launch does not need a command-line flag. It is **not** a
"JDK 25 supported" claim and does not change how `System.load` is called.

### (b) Implementation

- `obfuscator/.../NativeObfuscator.java`: the output-manifest write path now
  always emits a manifest via a new `buildOutputManifest` helper:
  - always includes `Enable-Native-Access: ALL-UNNAMED`;
  - preserves a more specific `Enable-Native-Access` value if the input already
    sets one;
  - preserves all other input attributes (`Main-Class`, etc.);
  - if the input JAR has no manifest, creates a minimal one
    (`Manifest-Version: 1.0` + the attribute).
- No CLI flag added. Loader load-calls unchanged. `--codegen` / `--ir-lower` /
  `--backend` defaults unchanged. Default codegen is still `legacy`.
- Docs: README usage note (`java -jar` honors the attribute; classpath launches
  still need `--enable-native-access=ALL-UNNAMED`; not a JDK 25 support badge),
  `docs/architecture/compatibility-jdk.md` JNI native-access row updated, and a
  new `docs/architecture/jep472-native-access.md`.

### (c) Tests (real counts)

Command:

```
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ManifestNativeAccessTest
```

Result: `BUILD SUCCESSFUL`.

- `by.radioegor146.CodegenModeTest` — tests=7, failures=0, errors=0, skipped=0
  (unchanged; confirms default CLI is still `legacy`).
- `by.radioegor146.ManifestNativeAccessTest` — tests=4, failures=0, errors=0,
  skipped=0 (new):
  - legacy run output JAR contains `Enable-Native-Access: ALL-UNNAMED` and
    preserves `Main-Class`;
  - legacy run with no input manifest creates a minimal manifest with the
    attribute;
  - existing specific `Enable-Native-Access` value is preserved (not
    overwritten);
  - absent input manifest yields `Manifest-Version: 1.0` + attribute, no
    `Main-Class`.

Environment: OpenJDK 21 (Temurin 25 was **not** used; the JDK 25 E2E was not
re-run, so no claim is made that the JEP 472 warning is gone).

### (d) Risk & review

- Ship-ready: **No**. Review required: **Yes**.
- Preconditions to check in review:
  - no "JDK 25 supported" badge/row was added and no default was flipped;
  - `--codegen` default remains `legacy`;
  - manifest semantics match JEP 472 (`java -jar` honors the attribute;
    classpath launches still need `--enable-native-access=ALL-UNNAMED`).
- Not covered: classpath launches still require the flag; the JDK 25 E2E warning
  is unchanged and not re-verified here.

---

## 中文

### (a) 内容与动机

生成的 JAR 通过 `native0.Loader` / `LoaderUnpack` / `LoaderPlain` 加载本地代码，
它们调用 `System.load` / `System.loadLibrary`。根据
[JEP 472](https://openjdk.org/jeps/472)，这些是*受限*方法：JDK 24+ 在未启用时默认
告警，未来版本可能将告警变为错误。已合入的 JDK 25 IR E2E
（`docs/benchmarks/ir-jdk25-e2e-corpus.md`，`5ac6ec5`）在每次转换运行中都记录了
该四行告警。

本次改动**仅为打包 + 文档**，为输出 JAR 提供前向兼容路径，使 `java -jar` 启动
无需命令行参数。这**不是**"支持 JDK 25"的声明，也不改变 `System.load` 的调用方式。

### (b) 实现

- `obfuscator/.../NativeObfuscator.java`：输出 manifest 写入路径现在通过新的
  `buildOutputManifest` 辅助方法始终写出 manifest：
  - 始终包含 `Enable-Native-Access: ALL-UNNAMED`；
  - 若输入已设置更具体的 `Enable-Native-Access` 值则保留该值；
  - 保留其他所有输入属性（`Main-Class` 等）；
  - 若输入 JAR 无 manifest，则创建最小 manifest（`Manifest-Version: 1.0` + 该属性）。
- 未新增 CLI 参数。加载调用不变。`--codegen` / `--ir-lower` / `--backend` 默认值
  不变。默认 codegen 仍为 `legacy`。
- 文档：README 使用说明、`docs/architecture/compatibility-jdk.md` 的 JNI
  native-access 行更新，以及新增 `docs/architecture/jep472-native-access.md`。

### (c) 测试（真实计数）

命令：

```
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ManifestNativeAccessTest
```

结果：`BUILD SUCCESSFUL`。

- `by.radioegor146.CodegenModeTest` — tests=7，failures=0，errors=0，skipped=0
  （未改动；确认默认 CLI 仍为 `legacy`）。
- `by.radioegor146.ManifestNativeAccessTest` — tests=4，failures=0，errors=0，
  skipped=0（新增）：
  - legacy 运行的输出 JAR 含 `Enable-Native-Access: ALL-UNNAMED` 且保留 `Main-Class`；
  - 无输入 manifest 的 legacy 运行创建含该属性的最小 manifest；
  - 已存在的具体 `Enable-Native-Access` 值被保留（不被覆盖）；
  - 无输入 manifest 时得到 `Manifest-Version: 1.0` + 属性，无 `Main-Class`。

环境：OpenJDK 21（**未**使用 Temurin 25；未重跑 JDK 25 E2E，因此不声称 JEP 472
告警已消失）。

### (d) 风险与评审

- 可发布：**否**。需要评审：**是**。
- 评审需确认的前提：
  - 未新增"支持 JDK 25"徽章/行，未翻转任何默认值；
  - `--codegen` 默认仍为 `legacy`；
  - manifest 语义符合 JEP 472（`java -jar` 生效；classpath 启动仍需
    `--enable-native-access=ALL-UNNAMED`）。
- 未覆盖：classpath 启动仍需该参数；JDK 25 E2E 告警未变且未在此重新验证。
