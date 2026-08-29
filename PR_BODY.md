# docs: Fable review of #141 JDK 25 IR E2E

## English

### (a) Scope

Independent Fable review of draft PR #141 (branch
`cursor/eval-ir-jdk25-e2e-6d81`, tip `a3ffcb2`), which records an opt-in
IR-mode behavioral E2E over the four checked-in JDK 25 fixtures. This
review branch adds only `docs/reviews/ir-jdk25-e2e-fable.md` and this
`PR_BODY.md`; no compiler, IR, evaluator, or interpreter source is touched,
and the E2E was not re-run.

### (b) Ship-ready?

**No.** The reviewed PR is a single-host behavioral measurement of four
small fixtures, and this review does not upgrade it. "JDK 25 supported"
remains an unmade claim, the `--codegen` default remains `legacy`, the
`#53` eval median remains `N/A`, and the JEP 472 restricted-native-access
warning plus the hybrid flexible-constructor result are open items.

### (c) Review verdict

**Accept with nits.** All five checks pass: (1) the diff versus
`origin/master` is docs-only (two added files, no support-badge or default
flips); (2) the write-up never claims "JDK 25 supported" and labels the
`Main$Validated.<init>(I)V` result a hybrid, not full IR; (3) commands,
host-21 versus Temurin-25.0.4.1+1 toolchains, checksums, and the 20/21
admission arithmetic are internally consistent and match the tree — the
diagnostic text, opcode 154 (`IFNE`), ASM 9.8, fixture list, and the
Adoptium tarball SHA-256 were each verified against source or the
publisher; (4) the JEP 472 warning is quoted verbatim and framed as a
warning that a future JDK turns into an error, not ignored; (5) `#53` is
not back-filled and no bench timings are invented. Beyond the checklist,
all four recorded stdout SHA-256 values were independently reproduced by
deriving each fixture's deterministic output from its committed source and
hashing it — every hash matches, corroborating that the byte-exact match
claims hash real content. Two non-blocking presentation nits are recorded
in the review doc (mixed exit-code/count notation in the summary total
row; a single hash column labeled as covering both files).

### (d) Preconditions / status

The production goal remains incomplete. Any future JDK 25 support claim
needs a wider corpus, more vendors and architectures, an
`--enable-native-access` story for the generated `native0.Loader`, and
handling for flexible constructor bodies that branch before `super(...)`.
Nothing in this review adds measurements or numbers.

## 中文

### (a) 范围

对草稿 PR #141（分支 `cursor/eval-ir-jdk25-e2e-6d81`，tip `a3ffcb2`）的
独立 Fable 评审；该 PR 记录了对仓库内四个 JDK 25 fixture 的 opt-in IR 模式
行为 E2E。本评审分支仅新增 `docs/reviews/ir-jdk25-e2e-fable.md` 和本
`PR_BODY.md`；未触碰编译器、IR、evaluator 或 interpreter 源码，也未重新
运行 E2E。

### (b) Ship-ready？

**No / 否。** 被评审的 PR 是单主机上对四个小 fixture 的一次行为测量，
本评审不改变其性质。"支持 JDK 25"仍是未做出的声明，`--codegen` 默认值仍为
`legacy`，#53 的 eval 中位数保持 `N/A`，JEP 472 受限 native 访问警告和
混合 flexible-constructor 结果仍是未决事项。

### (c) 评审结论

**接受，附非阻塞性 nit。** 五项检查全部通过：（1）相对 `origin/master`
的 diff 仅含两个新增文档文件，无支持徽章或默认值改动；（2）文档从未声称
"支持 JDK 25"，并将 `Main$Validated.<init>(I)V` 标记为混合结果而非纯 IR；
（3）命令、宿主 JDK 21 与 Temurin 25.0.4.1+1 工具链、校验和以及 20/21
admission 算术内部一致且与代码树相符 —— 诊断文本、opcode 154（`IFNE`）、
ASM 9.8、fixture 列表和 Adoptium tarball 的 SHA-256 均已对照源码或发布方
核实；（4）JEP 472 警告被逐字记录并定性为"未来 JDK 将变为错误"的警告，
而非被忽略；（5）#53 未被回填，未虚构任何 benchmark 数字。此外，四个
fixture 的 stdout 均可由其提交的源码确定性推导，独立复算的四个 SHA-256
全部与记录一致，证实逐字节匹配声明对应真实内容。评审文档中记录了两条
非阻塞性写作 nit（汇总行的 exit code 与计数记法混用；单列哈希表头覆盖
两个文件）。

### (d) 前置条件 / 状态

生产目标仍未完成。未来任何 JDK 25 支持声明都需要更大的语料、更多厂商与
架构、为生成的 `native0.Loader` 提供 `--enable-native-access` 方案，并
处理在 `super(...)` 之前存在分支的 flexible constructor body。本评审不
新增任何测量或数字。
