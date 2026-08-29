# Fable review: JEP 472 native-access packaging (#145)

Stacked review PR. Base: `cursor/jep472-native-access-6d81` (tip
`47661918a75eef4c8489f88596dfb5e59bb2f5dc`), **not** `master`. Adds one review
document; no compiler, loader, test, or doc-content changes to the increment
under review.

## English

### (a) What this PR is

An independent review of PR #145 only (the JEP 472 native-access packaging
increment). The deliverable is `docs/reviews/jep472-native-access-fable.md`.

**Verdict: accept-with-nits.** All six checklist items were verified against
the actual branch, not the PR body:

- Diff vs `origin/master` is exactly the six claimed files; the
  `NativeObfuscator.java` change is confined to the output-manifest write path.
  No constructor-split, interpreter, evaluator, or IR-frontend edits.
- CLI defaults remain `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`;
  no "JDK 25 supported" badge language anywhere.
- `buildOutputManifest` read from source: null input → minimal manifest
  (`Manifest-Version: 1.0` + `Enable-Native-Access: ALL-UNNAMED`); an existing
  `Enable-Native-Access` value is preserved (guarded put); all other input
  attributes carry through via manifest deep-copy. The entry-copy loop skips
  `META-INF/MANIFEST.MF`, so no duplicate entry is possible.
- Tests re-run on OpenJDK 21: `CodegenModeTest` tests=7, failures=0;
  `ManifestNativeAccessTest` tests=4, failures=0. `BUILD SUCCESSFUL`. The four
  new tests assert exactly the four claimed cases.
- Docs do not claim the #141 JDK 25 warning is gone; the JDK 25 E2E was not
  re-run and the docs say so. Classpath launches are documented as still
  needing `--enable-native-access=ALL-UNNAMED`.
- JEP 472 semantics stated accurately: the manifest attribute is honored for
  the application JAR (`java -jar`), not for classpath loads.

Nits (concrete, non-blocking): (1) the manifest is written as the last entry of
the output JAR — fine for `java -jar` / `JarFile`, but stream-based consumers
(`JarInputStream.getManifest()`) only see a first-entry manifest; pre-existing
ordering, worth a follow-up now that the manifest carries a meaningful
attribute. (2) The test fixture uses slash-form `Main-Class: example/Program`
instead of the conventional dotted form.

Ship-ready remains **No**.

### (b) Is this ship-ready?

No. The reviewed increment is packaging + docs; the JDK 25 E2E warning result
was not re-verified, and this review does not change that.

### (c) Testing

This PR *is* the review; its only artifact is a review document. Verification
performed for the review itself: full diff inspection vs `origin/master`,
source read of `buildOutputManifest` and the JAR write path, and a re-run of
the two test classes (`CC=gcc CXX=g++ ./gradlew :obfuscator:test
--rerun-tasks --tests by.radioegor146.CodegenModeTest --tests
by.radioegor146.ManifestNativeAccessTest`) with real counts 7 + 4, all passing.

### (d) Merge order

Stack this PR only on `cursor/jep472-native-access-6d81` (#145). It must not be
retargeted to `master`; it has no meaning without the increment it reviews.

---

## 中文

### (a) 本 PR 内容

仅针对 PR #145（JEP 472 native-access 打包增量）的独立评审。交付物为
`docs/reviews/jep472-native-access-fable.md`。

**结论：accept-with-nits（接受，附小问题）。** 六项检查均基于分支实际内容而非
PR 描述验证：

- 相对 `origin/master` 的 diff 恰好为声称的六个文件；`NativeObfuscator.java`
  改动仅限输出 manifest 写入路径。无 constructor-split、解释器、求值器或 IR
  前端改动。
- CLI 默认值仍为 `--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`；
  无任何"支持 JDK 25"的表述。
- 从源码核对 `buildOutputManifest`：无输入 manifest → 最小 manifest
  （`Manifest-Version: 1.0` + `Enable-Native-Access: ALL-UNNAMED`）；已存在的
  `Enable-Native-Access` 值被保留（有守卫的 put）；其他输入属性经深拷贝完整
  保留。条目复制循环跳过 `META-INF/MANIFEST.MF`，不可能产生重复条目。
- 在 OpenJDK 21 上重跑测试：`CodegenModeTest` tests=7，failures=0；
  `ManifestNativeAccessTest` tests=4，failures=0。`BUILD SUCCESSFUL`。四个新
  测试确实断言了声称的四种情形。
- 文档未声称 #141 的 JDK 25 告警已消失；JDK 25 E2E 未重跑且文档如实说明。
  classpath 启动仍需 `--enable-native-access=ALL-UNNAMED`，文档已写明。
- JEP 472 语义表述准确：manifest 属性对应用 JAR（`java -jar`）生效，对
  classpath 加载不生效。

小问题（具体、不阻塞）：（1）manifest 被写为输出 JAR 的最后一个条目——对
`java -jar` / `JarFile` 没有影响，但基于流的读取方
（`JarInputStream.getManifest()`）只识别首条目 manifest；此顺序为既有行为，
在 manifest 携带有实际语义的属性后值得后续跟进。（2）测试夹具使用斜杠形式的
`Main-Class: example/Program`，而非常规点号形式。

可发布状态仍为**否**。

### (b) 是否可发布？

否。被评审的增量为打包 + 文档；JDK 25 E2E 告警结果未重新验证，本评审不改变
这一点。

### (c) 测试

本 PR 本身即评审；唯一产物是评审文档。评审过程中执行的验证：相对
`origin/master` 的完整 diff 检查、`buildOutputManifest` 与 JAR 写入路径的源码
阅读，以及重跑两个测试类（`CC=gcc CXX=g++ ./gradlew :obfuscator:test
--rerun-tasks --tests by.radioegor146.CodegenModeTest --tests
by.radioegor146.ManifestNativeAccessTest`），真实计数 7 + 4，全部通过。

### (d) 合并顺序

本 PR 仅应堆叠在 `cursor/jep472-native-access-6d81`（#145）之上，不应改指向
`master`；脱离其所评审的增量本 PR 无意义。
