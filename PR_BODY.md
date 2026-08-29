# docs: JDK 25 IR E2E corpus

## English

### (a) Scope

Run a real opt-in IR-mode behavioral E2E over all four checked-in JDK 25
fixtures (`CompactSourceModuleImportE2E`, `FlexibleConstructorBodiesE2E`,
`ScopedValuesE2E`, `StreamGatherersE2E`) and record only measured results in
the new `docs/benchmarks/ir-jdk25-e2e-corpus.md`. Base is `origin/master` at
`a7e5453`. The host JDK is 21; a real JDK 25 (Eclipse Temurin 25.0.4.1+1) was
downloaded from Adoptium, SHA-256 verified, and used for `javac --release 25
-g`, the HotSpot oracle runs, the JNI headers, and the transformed native
runs. No compiler, IR, evaluator, interpreter, `Main.java`, or
`project-status.md` file is changed; this branch is docs-only.

### (b) Ship-ready?

**No.** This is one behavioral measurement of four small fixtures on one
Linux x86-64 host. It must not be read as "JDK 25 supported": the default
code generator remains `legacy`, one constructor stayed as plain Java
bytecode, and the generated loader trips the JEP 472 restricted-native-access
warning that a future JDK will turn into an error. No benchmark numbers were
measured; the issue #53 entry stays `N/A`.

### (c) Measured result

All four fixtures compiled with a real `javac` 25 (exit 0, no preview flags),
transpiled with `--codegen=ir` (exit 0), configured and built under CMake
with `CC=gcc CXX=g++` against `/opt/jdk25/include` JNI (exit 0), and the
transformed JARs all exited 0 with byte-exact stdout matches against the
JDK 25 HotSpot oracle (SHA-256 pairs recorded per fixture).

IR admission by exact class/method/descriptor join: 20 of 21 code-bearing
input methods reached IR with zero legacy fallbacks and zero missing methods.
The single exception is `Main$Validated.<init>(I)V`
(`FlexibleConstructorBodiesE2E`), whose flexible constructor body branches
before `super(...)`; the transpiler logged `Control flow before the
this/super call cannot be split safely at bytecode instruction 8 (opcode
154)` and left that constructor unchanged, so that fixture's pass is a hybrid
result.

### (d) Integration evidence

The shadow JAR built with `BUILD SUCCESSFUL`. Each CMake configure identified
GNU 13.3.0 and `Found JNI: /opt/jdk25/include`; each build ended with `[100%]
Built target native_library`. Every excluded raw `// IR codegen:` marker (6
total) was a transpiler-inserted `<clinit>`, consistent with the JDK 17/21
corpus docs. Native stderr was not empty on JDK 25: every transformed run
printed the four-line JEP 472 `System::load` restricted-method warning, which
is documented verbatim, with oracle stderr empty. Exact commands, both
`java -version` outputs (host 21.0.10 and Temurin 25.0.4.1+1), the
non-interactive JDK 25 install steps, per-method admission lists, and the
per-fixture stdout SHA-256 table are all in the doc.

## 中文

### (a) 范围

对仓库内全部四个 JDK 25 fixture（`CompactSourceModuleImportE2E`、
`FlexibleConstructorBodiesE2E`、`ScopedValuesE2E`、`StreamGatherersE2E`）
执行真实的 opt-in IR 模式行为 E2E，并只在新文档
`docs/benchmarks/ir-jdk25-e2e-corpus.md` 中记录实测结果。基线为
`origin/master`（`a7e5453`）。宿主机 JDK 为 21；从 Adoptium 下载了真实的
JDK 25（Eclipse Temurin 25.0.4.1+1），经 SHA-256 校验后用于
`javac --release 25 -g` 编译、HotSpot oracle 运行、JNI 头文件和转换后
native 运行。不修改编译器、IR、evaluator、interpreter、`Main.java` 或
`project-status.md`；本分支仅含文档。

### (b) Ship-ready？

**No / 否。** 这只是在一台 Linux x86-64 主机上对四个小 fixture 的一次行为
测量，不得解读为"支持 JDK 25"：默认代码生成器仍为 `legacy`，有一个构造器
保留为普通 Java 字节码，且生成的 loader 触发 JEP 472 受限 native 访问警告
（未来 JDK 将把它变成错误）。未测量任何 benchmark 数字；issue #53 条目
保持 `N/A`。

### (c) 实测结果

四个 fixture 均用真实 `javac` 25 编译成功（exit 0，无 preview 标志）、
`--codegen=ir` 转译成功（exit 0）、CMake 以 `CC=gcc CXX=g++` 和
`/opt/jdk25/include` JNI 配置并构建成功（exit 0）；四个转换后 JAR 均以
exit 0 结束，stdout 与 JDK 25 HotSpot oracle 逐字节一致（每个 fixture 的
SHA-256 均已记录）。

按 class/method/descriptor 精确 join 的 IR admission：21 个含代码输入方法中
20 个进入 IR，0 个 legacy fallback，0 个缺失。唯一例外是
`FlexibleConstructorBodiesE2E` 的 `Main$Validated.<init>(I)V`：其 flexible
constructor body 在 `super(...)` 之前有分支，转译器记录了 `Control flow
before the this/super call cannot be split safely at bytecode instruction 8
(opcode 154)` 并保留该构造器不变，因此该 fixture 的通过属于混合结果。

### (d) 集成证据

shadow JAR 构建 `BUILD SUCCESSFUL`。每次 CMake configure 识别出 GNU 13.3.0
并报告 `Found JNI: /opt/jdk25/include`；每次构建以 `[100%] Built target
native_library` 结束。被排除的 6 个原始 `// IR codegen:` marker 全部是
转译器插入的 `<clinit>`，与 JDK 17/21 语料文档一致。JDK 25 上 native stderr
非空：每个转换后运行都打印了四行 JEP 472 `System::load` 受限方法警告，
文档已逐字记录；oracle stderr 为空。精确命令、两套 `java -version` 输出
（宿主 21.0.10 与 Temurin 25.0.4.1+1）、非交互式 JDK 25 安装步骤、逐方法
admission 列表和逐 fixture stdout SHA-256 表均在文档中。
