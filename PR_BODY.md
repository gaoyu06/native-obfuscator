## (a) 改动范围 / Change scope

新增 6 个由 `javac --release 17` 编译的独立行为 fixture：switch 表达式、
`instanceof` 模式匹配、text block、`Stream.toList()`、record compact
constructor，以及 sealed interface + record dispatch。它们由现有
`TestsGenerator` 自动发现，无需修改 harness。

Adds six standalone `javac --release 17` behavioral fixtures covering switch
expressions, `instanceof` patterns, text blocks, `Stream.toList()`, a compact
record constructor, and sealed-interface dispatch through records. Existing
recursive discovery picks them up without a harness change.

同时新增 `docs/benchmarks/ir-jdk17-e2e-corpus.md`，记录当前 master 上旧 5 个
加新 6 个 fixture 的真实 `--codegen=ir` oracle/native 测量。未修改 compiler、
opcode interpreter、reader/evaluator 或默认 codegen。

Also records a real oracle/native `--codegen=ir` measurement for all five
existing plus six new fixtures. No compiler, opcode-interpreter,
reader/evaluator, or default-codegen source changed.

## (b) 是否完成生产目标 / Is the production goal complete?

**No / 否。**

结果仅为一台 Linux x86-64 VM、OpenJDK 21 HotSpot 与 GNU 13.3 toolchain 上
11 个 Java 17 classfile 程序的证据。它不是 “JDK 17 supported”，不构成 runtime、
平台、架构或完整 JDK API 兼容矩阵；CLI 默认值仍为 `--codegen=legacy`。

This is evidence for eleven Java 17 classfile programs on one Linux x86-64 VM
with OpenJDK 21 HotSpot and GNU 13.3. It is not “JDK 17 supported,” not a
runtime/platform/architecture/JDK-API matrix, and the CLI default remains
`--codegen=legacy`.

## (c) 审阅重点 / Review focus

- 确认 6 个新 fixture 的 stdout 确定且各自覆盖不同语义面；
- 确认 admission 统计只包含 input JAR 中具有 `Code:` 的方法，并排除 21 个
  obfuscator 生成的方法 marker；
- 确认行为结论来自 11 次独立 HotSpot oracle、11 次独立 native build 与
  11 次 transformed-JAR execution，而非 admission 代替行为测试；
- 确认文档中的单机边界与 `legacy` 默认值说明足够明确。

- Confirm the six outputs are deterministic and exercise distinct semantics.
- Confirm admission includes only code-bearing input methods and excludes 21
  generated-method markers.
- Confirm the behavioral claim comes from eleven independent oracle runs,
  native builds, and transformed-JAR runs—not admission alone.
- Confirm the one-machine limitation and unchanged `legacy` default are clear.

## (d) 验证与前置条件 / Verification and review preconditions

实际运行 / Commands actually run:

```bash
uname -a
java -version
javac -version
gcc --version
g++ --version
cmake --version
CC=gcc CXX=g++ ./gradlew :obfuscator:shadowJar --console=plain
```

对 11 个 fixture 均实际运行以下流程 / The following flow was actually run
for each of the eleven fixtures:

```bash
javac --release 17 -g -d "$classes" "$source_dir/Main.java"
jar --create --file "$work/input.jar" --main-class Main -C "$classes" .
(cd "$work" && java -Dseed=1337 -jar "$work/input.jar")
java -jar "$obfuscator" "$work/input.jar" "$output" --codegen=ir
(cd "$output/cpp" && CC=gcc CXX=g++ cmake .)
(cd "$output/cpp" && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 "$output/cpp/build/lib/libnative_library.so" \
  "$pack/native0/x64-linux.so"
jar --update --file "$output/input.jar" \
  -C "$pack" native0/x64-linux.so
(cd "$output" && java -Djava.library.path=. -Dseed=1337 \
  -Dplatform=HOTSPOT -Dtest.src="$work" -jar "$output/input.jar")
cmp -s "$work/oracle.stdout" "$work/native.stdout"
javap -p -s -c -classpath "$work/input.jar" "$class_name"
```

实测结果 / Measured result:

- `javac --release 17`: **11/11 exit 0**
- IR transpilation: **11/11 exit 0**
- CMake configure/build: **11/11 + 11/11 exit 0**
- transformed JAR: **11/11 exit 0**
- exact stdout parity: **11/11**
- input method admission: **82/82 IR, 0 fallback, 0 missing**

未运行 broad `TestsGenerator`：它覆盖全部 ClassicTest 数据并走 legacy API 的
三个 `Platform` 值；本 PR 已直接对全部 11 个 JDK 17 fixture 完成要求的 IR native
E2E。因未修改 compiler，未额外声称 focused compiler-suite 结果。

The broad `TestsGenerator` suite was not run because it covers all ClassicTest
data and the legacy API across three `Platform` values. This PR instead ran
the required IR-native E2E directly for every JDK 17 fixture. No focused
compiler-suite result is claimed because compiler code was not changed.
