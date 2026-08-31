# Project status on master / master 现状

Last updated after leftover inventory remasurement
[#391](https://github.com/gaoyu06/native-obfuscator/pull/391)
(parent XML 677 from
[#390](https://github.com/gaoyu06/native-obfuscator/pull/390);
leftover inventory
[#391](https://github.com/gaoyu06/native-obfuscator/pull/391)
on leftover-docs
[#389](https://github.com/gaoyu06/native-obfuscator/pull/389)
`1db7af5`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR,
0 leftovers; not coverage-complete; not a JDK support badge). Active process:
[current-goal.md](current-goal.md) (fast-model increments, test gate,
Fable 5 reserved for hard work).
This page is the current public status. It must not be read as a support
matrix. The long maintainer brief in
[goal-status-and-options.md](goal-status-and-options.md) is historical.

本页是当前公开现状。现行目标见
[current-goal.md](current-goal.md)：先把方法体全部迁到 IR，再废弃
legacy。不能当成 JDK 支持矩阵。

## What landed / 已落地

- **Legacy generator (default).** Snippet substitution through
  `cppsnippets.properties` remains the CLI and API default (`--codegen=legacy`).
- **Opt-in IR.** `--codegen=ir` lowers admitted methods through a typed CFG
  (i32 / i64 / f32 / f64 / reference) to structured C++/JNI. Phase 19 adds
  `LAND`/`LOR`/`LXOR` and `LSHL`/`LSHR`/`LUSHR`. Phase 20
  ([#134](https://github.com/gaoyu06/native-obfuscator/pull/134); Sol accept
  [#135](https://github.com/gaoyu06/native-obfuscator/pull/135); Fable
  accept-with-nits [#136](https://github.com/gaoyu06/native-obfuscator/pull/136))
  adds `LDIV`/`LREM`/`LNEG` via dedicated `LongDivRem` / `LongUnary` nodes.
  [#153](https://github.com/gaoyu06/native-obfuscator/pull/153) (Fable accept
  [#156](https://github.com/gaoyu06/native-obfuscator/pull/156)) admits
  `LCMP` as `LongCompare` (I64/I64 → I32, signed ternary, not a subtract).
  [#157](https://github.com/gaoyu06/native-obfuscator/pull/157) admits
  `IF_ACMPEQ` / `IF_ACMPNE` as `ReferenceCompareBranch` (identity
  `==` / `!=`).
  [#158](https://github.com/gaoyu06/native-obfuscator/pull/158) admits
  `monitorenter` / `monitorexit` and synchronized methods (JNI
  `MonitorEnter` / `MonitorExit`, LIFO pairing check).
  [#159](https://github.com/gaoyu06/native-obfuscator/pull/159) admits
  preprocessor-lowerable `invokedynamic` (string concat, lambda
  metafactory, `ObjectMethods.bootstrap`) via a method copy.
  [#161](https://github.com/gaoyu06/native-obfuscator/pull/161) admits
  proven static `ConstantDynamic` `LDC` through a one-time cached
  resolver, plus raw `MethodHandle` / `MethodType` `LDC`.
  [#167](https://github.com/gaoyu06/native-obfuscator/pull/167) admits
  primitive `Class` `LDC` via wrapper `TYPE` `GETSTATIC` on a private
  copy.
  [#168](https://github.com/gaoyu06/native-obfuscator/pull/168) admits
  the same proven `ConstantDynamic` shape in a public non-annotation
  interface via a hidden companion cache and an uncached interface
  bootstrap bridge. Unsafe condy shapes (non-static, varargs,
  malformed, cyclic) stay reject-before-mutation. Unsupported methods
  fall back per-method.
  Rejected constructors are restored from the original class bytes so
  indy preprocessor markers are not left in output.
  [#146](https://github.com/gaoyu06/native-obfuscator/pull/146) admits
  prefix-local branches (every target still in the prefix) so a JEP 513-style
  prologue like `if (...) throw; super(...)` can split. Prefix + this/super
  stay in bytecode.
  [#160](https://github.com/gaoyu06/native-obfuscator/pull/160) admits
  prefix `ASTORE` into reference/array parameter slots.
  [#163](https://github.com/gaoyu06/native-obfuscator/pull/163) forwards
  definitely assigned prefix extra locals (reference + exact primitive
  carriers, including category-2 slots) through trailing hidden-bridge
  arguments.
  [#164](https://github.com/gaoyu06/native-obfuscator/pull/164) packs
  gapped extras and remaps suffix accesses onto those packed slots.
  [#165](https://github.com/gaoyu06/native-obfuscator/pull/165) admits
  a strict shared-label multi-super diamond (exactly one chain call per
  successful path, one join, one hidden bridge).
  [#166](https://github.com/gaoyu06/native-obfuscator/pull/166) admits
  try/catch wholly in the prefix (kept in bytecode) while suffix-only
  regions stay in the IR body.
  [#170](https://github.com/gaoyu06/native-obfuscator/pull/170) admits
  prefix `ASTORE 0` when every such store and each selected this/super
  call still names the original receiver.
  [#171](https://github.com/gaoyu06/native-obfuscator/pull/171) admits
  a suffix protected range whose prefix handler is an isolated
  `POP; RETURN` (cloned into the IR suffix).
  [#172](https://github.com/gaoyu06/native-obfuscator/pull/172) admits
  two reachable this/super calls whose suffixes are non-empty,
  straight-line, and instruction-identical (first copy rewritten to a
  shared join).
  [#173](https://github.com/gaoyu06/native-obfuscator/pull/173) admits
  the same two-call shape when each chain call is immediately followed
  by `RETURN` (IR body is `RETURN` only).
  [#174](https://github.com/gaoyu06/native-obfuscator/pull/174) admits
  `ILOAD` of a declared int-family argument then `IFNE` to the shared
  suffix after the first chain call (else `RETURN`).
  [#175](https://github.com/gaoyu06/native-obfuscator/pull/175) admits
  an extra that is unassigned on an immediate-return sibling path but
  has one compatible type on every path that reaches the hidden
  bridge.
  [#176](https://github.com/gaoyu06/native-obfuscator/pull/176) admits
  three or more reachable this/super calls that each immediately
  `RETURN` with the original receiver and direct declared-argument
  loads.
  [#177](https://github.com/gaoyu06/native-obfuscator/pull/177) admits
  the remaining unary int-zero branches and all six `IF_ICMPxx` after
  the first chain call when operands are declared int-family
  `ILOAD`s.
  [#178](https://github.com/gaoyu06/native-obfuscator/pull/178) admits
  exact two-call post-chain `TABLESWITCH` / `LOOKUPSWITCH` when the
  key is a direct declared int-family `ILOAD` and every case/default
  is the shared suffix, an immediate prefix `RETURN`, or a direct
  suffix `GOTO`.
  [#179](https://github.com/gaoyu06/native-obfuscator/pull/179) admits
  3+ immediate-`RETURN` chain arguments that are int-family constants
  (`ICONST_*` / `BIPUSH` / `SIPUSH` / `LDC` Integer) or one `INEG`
  over a declared int-family `ILOAD`.
  [#180](https://github.com/gaoyu06/native-obfuscator/pull/180) admits
  3+ reachable this/super calls whose empty or nonempty straight-line
  suffix copies are instruction-identical (one join, one hidden
  bridge).
  [#182](https://github.com/gaoyu06/native-obfuscator/pull/182) admits
  one leaf-only `IADD` whose both operands are already-proven
  int-family chain inputs.
  [#183](https://github.com/gaoyu06/native-obfuscator/pull/183) admits
  one leaf-only `ISUB` or `IMUL` under the same one-level proof.
  [#184](https://github.com/gaoyu06/native-obfuscator/pull/184) admits
  a suffix-protected range whose isolated prefix handler is
  `POP; GOTO ret` with an isolated prefix `RETURN`.
  [#185](https://github.com/gaoyu06/native-obfuscator/pull/185) admits
  one leaf-only `IAND`, `IOR`, or `IXOR` under the same one-level
  proof.
  [#186](https://github.com/gaoyu06/native-obfuscator/pull/186) admits
  one leaf-only `ISHL`, `ISHR`, or `IUSHR` under the same one-level
  proof.
  [#187](https://github.com/gaoyu06/native-obfuscator/pull/187) admits
  a suffix-protected range whose isolated prefix handler is
  `ASTORE n; RETURN` or `ASTORE n; GOTO ret` (`n` is a non-receiver
  reference slot).
  [#188](https://github.com/gaoyu06/native-obfuscator/pull/188) admits
  the same suffix-range shape whose isolated prefix handler is
  `ATHROW` or `ASTORE n; ALOAD n; ATHROW`.
  [#189](https://github.com/gaoyu06/native-obfuscator/pull/189) admits
  exactly two reachable this/super calls whose nonempty
  straight-line suffixes differ, via one hidden bridge and a
  trailing constant path id.
  [#190](https://github.com/gaoyu06/native-obfuscator/pull/190) admits
  two through eight pairwise-distinct nonempty linear suffixes on
  that same path-id bridge (`IFNE` for two paths, `TABLESWITCH`
  for three or more).
  [#192](https://github.com/gaoyu06/native-obfuscator/pull/192) forwards
  proven prefix extras through that path-id bridge (extras before the
  trailing path-id `int`).
  [#193](https://github.com/gaoyu06/native-obfuscator/pull/193) admits
  one closed unary or binary int-family branch inside either of
  exactly two distinct suffixes.
  [#194](https://github.com/gaoyu06/native-obfuscator/pull/194) lifts
  that same closed-branch proof onto the 3–8-call path-id
  `TABLESWITCH` bridge.
  [#195](https://github.com/gaoyu06/native-obfuscator/pull/195) admits
  one closed `TABLESWITCH` or `LOOKUPSWITCH` inside a distinct suffix
  when the key is a proven int-family `ILOAD` and every arm stays
  in-suffix and reaches `RETURN`.
  [#196](https://github.com/gaoyu06/native-obfuscator/pull/196) admits
  a path-id set that contains an identical suffix pair if at least
  one other suffix is CFG-distinct.
  [#197](https://github.com/gaoyu06/native-obfuscator/pull/197) admits
  a two-level tree of the already-proven non-trapping int binaries
  as chain-call inputs.
  [#198](https://github.com/gaoyu06/native-obfuscator/pull/198) admits
  one-level leaf-only `IDIV`/`IREM` as chain-call inputs (trapping
  stays in the retained prefix).
  [#200](https://github.com/gaoyu06/native-obfuscator/pull/200) admits
  prefix-only exception tables on identical-copy and path-id
  multi-super constructors, and wholly-in-one-suffix tables on
  path-id suffixes.
  [#201](https://github.com/gaoyu06/native-obfuscator/pull/201) admits
  a suffix-protected try on one path-id suffix that targets an
  already-proven isolated prefix handler.
  [#202](https://github.com/gaoyu06/native-obfuscator/pull/202) admits
  single-chain receiver-alias forwarding: save the original
  receiver, overwrite local 0, and invoke this/super through the
  proven alias.
  [#203](https://github.com/gaoyu06/native-obfuscator/pull/203) admits
  the same alias proof on the two-call shared-join diamond.
  [#204](https://github.com/gaoyu06/native-obfuscator/pull/204) admits
  the same alias proof on bounded path-id distinct suffixes.
  [#205](https://github.com/gaoyu06/native-obfuscator/pull/205) admits
  the same alias proof on two-or-more identical suffix copies.
  [#206](https://github.com/gaoyu06/native-obfuscator/pull/206) admits
  prefix-assigned extras on those identical copies, including a
  suffix read of the alias extra.
  [#208](https://github.com/gaoyu06/native-obfuscator/pull/208) admits
  wholly-in-canonical-suffix tables and relocated isolated prefix
  handlers on identical-copy normalization.
  [#209](https://github.com/gaoyu06/native-obfuscator/pull/209) admits
  an isolated method-end handler for a try wholly in one proven suffix.
  [#210](https://github.com/gaoyu06/native-obfuscator/pull/210) admits
  a three-level tree of the already-proven non-trapping int binaries
  as chain-call inputs.
  [#211](https://github.com/gaoyu06/native-obfuscator/pull/211) admits
  `IDIV`/`IREM` as inner nodes of that same three-level walker
  (trapping stays in the retained prefix).
  [#212](https://github.com/gaoyu06/native-obfuscator/pull/212) admits
  exactly four binary levels of that walker.
  [#213](https://github.com/gaoyu06/native-obfuscator/pull/213) admits
  one leaf-only `LADD` as a long chain-call input.
  [#214](https://github.com/gaoyu06/native-obfuscator/pull/214) admits
  leaf-only `LSUB`/`LMUL` as long chain-call inputs.
  [#215](https://github.com/gaoyu06/native-obfuscator/pull/215) admits
  leaf-only `LAND`/`LOR`/`LXOR` as long chain-call inputs.
  [#216](https://github.com/gaoyu06/native-obfuscator/pull/216) admits
  leaf-only `LSHL`/`LSHR`/`LUSHR` as long chain-call inputs
  (long leaf value plus int-family leaf count).
  [#217](https://github.com/gaoyu06/native-obfuscator/pull/217) admits
  leaf-only `LDIV`/`LREM` as long chain-call inputs
  (trapping stays in the retained prefix).
  [#218](https://github.com/gaoyu06/native-obfuscator/pull/218) admits
  one `LNEG` over a declared long `LLOAD` as a long leaf.
  [#219](https://github.com/gaoyu06/native-obfuscator/pull/219) admits
  exactly two nested long binary levels (including mixed inner `LDIV`
  and an outer long shift whose count stays an int-family leaf).
  [#220](https://github.com/gaoyu06/native-obfuscator/pull/220) admits
  exactly three nested long binary levels (including mixed inner/outer
  `LDIV` and an outer `LSHL`).
  [#221](https://github.com/gaoyu06/native-obfuscator/pull/221) admits
  exactly four nested long binary levels (including mixed inner/outer
  `LDIV` and an outer `LSHL`).
  [#222](https://github.com/gaoyu06/native-obfuscator/pull/222) admits
  one leaf-only `FADD` over declared float loads / float constants.
  [#223](https://github.com/gaoyu06/native-obfuscator/pull/223) admits
  leaf-only `FSUB` and `FMUL` over the same float leaves.
  [#224](https://github.com/gaoyu06/native-obfuscator/pull/224) admits
  leaf-only `FDIV` and `FREM` over the same float leaves.
  [#225](https://github.com/gaoyu06/native-obfuscator/pull/225) admits
  one `FNEG` over a declared float `FLOAD` as a float leaf.
  [#226](https://github.com/gaoyu06/native-obfuscator/pull/226) admits
  exactly two nested float binary levels, including mixed inner/outer
  `FDIV` and a two-sided `FADD` tree.
  [#227](https://github.com/gaoyu06/native-obfuscator/pull/227) admits
  exactly three nested float binary levels, including mixed inner/outer
  `FDIV`.
  [#228](https://github.com/gaoyu06/native-obfuscator/pull/228) admits
  exactly four nested float binary levels, including mixed inner/outer
  `FDIV`.
  [#229](https://github.com/gaoyu06/native-obfuscator/pull/229) admits
  one leaf-only `DADD` over declared double loads / double constants.
  [#230](https://github.com/gaoyu06/native-obfuscator/pull/230) admits
  leaf-only `DSUB` and `DMUL` over the same double leaves.
  [#231](https://github.com/gaoyu06/native-obfuscator/pull/231) admits
  leaf-only `DDIV` and `DREM` over the same double leaves.
  [#232](https://github.com/gaoyu06/native-obfuscator/pull/232) admits
  one `DNEG` over a declared double `DLOAD` as a double leaf.
  [#233](https://github.com/gaoyu06/native-obfuscator/pull/233) admits
  exactly two nested double binary levels, including mixed inner/outer
  `DDIV`.
  [#234](https://github.com/gaoyu06/native-obfuscator/pull/234) admits
  exactly three nested double binary levels, including mixed inner/outer
  `DDIV`.
  [#235](https://github.com/gaoyu06/native-obfuscator/pull/235) admits
  exactly four nested double binary levels, including mixed inner/outer
  `DDIV`.
  [#236](https://github.com/gaoyu06/native-obfuscator/pull/236) admits
  a prefix extra-local copy of a declared double `DLOAD` as a double
  chain-input leaf.
  [#237](https://github.com/gaoyu06/native-obfuscator/pull/237) admits
  a prefix extra-local copy of a declared float `FLOAD` as a float
  chain-input leaf.
  [#238](https://github.com/gaoyu06/native-obfuscator/pull/238) admits
  a prefix extra-local copy of a declared long `LLOAD` as a long
  chain-input leaf.
  [#239](https://github.com/gaoyu06/native-obfuscator/pull/239) admits
  a prefix extra-local copy of a declared int-family `ILOAD` as an
  int-family chain-input leaf, including already-admitted int binaries.
  [#240](https://github.com/gaoyu06/native-obfuscator/pull/240) raises the
  int/long/float/double chain-input binary budget from 4 to 8 and admits
  the former five-level leftover fixtures.
  [#241](https://github.com/gaoyu06/native-obfuscator/pull/241) expands
  well-formed `jsr`/`ret` with ASM `JSRInlinerAdapter` on a private method
  copy before CFG construction, including a straight-line constructor
  prefix subroutine. Malformed subroutines stay reject-before-mutation.
  [#242](https://github.com/gaoyu06/native-obfuscator/pull/242) admits
  proven prefix extra-local long copies as long-shift values and as
  `LDIV`/`LREM` operands.
  [#243](https://github.com/gaoyu06/native-obfuscator/pull/243) admits
  one `LNEG` over a proven prefix extra-local long copy. Constant,
  double, and computed `LNEG` stay rejected.
  [#244](https://github.com/gaoyu06/native-obfuscator/pull/244) admits
  `ALOAD` of an unchanged declared array argument, a constant index,
  and `AALOAD` as a constructor chain-input. Unproven indexes and
  other reference computations stay rejected.
  [#245](https://github.com/gaoyu06/native-obfuscator/pull/245) admits
  a proven prefix extra-local int copy as the count of `LSHL`,
  `LSHR`, and `LUSHR`. Computed, negated, and type-wrong extra-local
  long counts stay rejected.
  [#246](https://github.com/gaoyu06/native-obfuscator/pull/246) admits
  one `FNEG` over a proven prefix extra-local float copy. Constant,
  double, and computed `FNEG` stay rejected.
  [#247](https://github.com/gaoyu06/native-obfuscator/pull/247) admits
  one `DNEG` over a proven prefix extra-local double copy. Constant,
  double, and computed `DNEG` stay rejected.
  [#248](https://github.com/gaoyu06/native-obfuscator/pull/248) admits
  one `INEG` over a proven prefix extra-local int copy. Constant,
  double, and computed `INEG` stay rejected.
  [#249](https://github.com/gaoyu06/native-obfuscator/pull/249) admits
  `ALOAD` of an unchanged declared `int[]` argument, a constant index,
  and `IALOAD` as an int-family chain-input leaf. Computed indexes,
  extra-local arrays, and other array-load families stay rejected.
  [#250](https://github.com/gaoyu06/native-obfuscator/pull/250) raises the
  int/long/float/double chain-input binary budget from 8 to 16 and admits
  the former nine-level leftover fixtures. Seventeen-or-more nested
  binaries stay rejected.
  [#251](https://github.com/gaoyu06/native-obfuscator/pull/251) admits
  `AALOAD` from a proven prefix extra-local copy of an unchanged declared
  reference-array argument at a constant index. Computed extra-local
  stores, overwritten copies, prior array stores, primitive-array
  results, and non-constant indexes stay rejected.
  [#252](https://github.com/gaoyu06/native-obfuscator/pull/252) admits
  `IALOAD` from a proven prefix extra-local copy of an unchanged declared
  `int[]` argument at a constant index. Computed extra-local stores,
  overwritten copies, prior array stores, non-`int[]` sources, and
  computed indexes stay rejected.
  [#253](https://github.com/gaoyu06/native-obfuscator/pull/253) admits
  `BALOAD`, `CALOAD`, and `SALOAD` from an unchanged declared `[B`/`[Z`,
  `[C`, or `[S` argument at a constant index. Computed indexes,
  extra-local indexes, extra-local array sources, and opcode/type
  mismatches stay rejected.
  [#254](https://github.com/gaoyu06/native-obfuscator/pull/254) admits
  `AALOAD` indexes that are one direct declared `ILOAD` or one `ILOAD` of
  a proven prefix extra-local int copy. Computed, `INEG`, computed-store,
  and unproven extra-local indexes stay rejected.
  [#255](https://github.com/gaoyu06/native-obfuscator/pull/255) admits
  `IALOAD`, `BALOAD`, `CALOAD`, and `SALOAD` indexes that are one direct
  declared `ILOAD` or one `ILOAD` of a proven prefix extra-local int copy.
  Computed and `INEG` indexes stay rejected.
  [#256](https://github.com/gaoyu06/native-obfuscator/pull/256) admits
  `BALOAD`, `CALOAD`, and `SALOAD` from a proven prefix extra-local copy
  of an unchanged declared `[B`/`[Z`, `[C`, or `[S` argument at a
  constant index. Computed extra-local stores, overwritten copies, prior
  array stores, and unproven indexes stay rejected.
  [#257](https://github.com/gaoyu06/native-obfuscator/pull/257) admits
  `LALOAD`, `FALOAD`, and `DALOAD` from an unchanged declared `[J`,
  `[F`, or `[D` argument at an int-family constant index. Extra-local
  wide-array sources, computed or extra-local indexes, overwritten
  arrays, prior array stores, and opcode/type mismatches stay rejected.
  [#258](https://github.com/gaoyu06/native-obfuscator/pull/258) admits
  those same loads from a proven prefix extra-local copy of an unchanged
  declared `[J`, `[F`, or `[D` argument at a constant index. Computed
  extra-local stores, overwritten copies, prior array stores, and
  unproven indexes stay rejected.
  [#259](https://github.com/gaoyu06/native-obfuscator/pull/259) admits
  `LALOAD`, `FALOAD`, and `DALOAD` indexes that are one direct declared
  `ILOAD` or one `ILOAD` of a proven prefix extra-local int copy.
  Computed, `INEG`, computed-store, and overwritten extra-local indexes
  stay rejected.
  [#260](https://github.com/gaoyu06/native-obfuscator/pull/260) admits
  `AALOAD`, `IALOAD`, `BALOAD`, `CALOAD`, and `SALOAD` when both the
  array source and the index are proven prefix extra-local copies.
  Computed stores, overwritten copies, prior array stores, and
  computed/`INEG` indexes stay rejected.
  [#261](https://github.com/gaoyu06/native-obfuscator/pull/261) keeps
  unproven prefix→suffix jumps and switches fail-closed and adds
  reject-before-mutation plus Java 8 verification for skip-init and
  unproven post-chain switch variants. No new crossing shape is admitted.
  [#262](https://github.com/gaoyu06/native-obfuscator/pull/262) admits
  `LALOAD`, `FALOAD`, and `DALOAD` when both the matching wide-array
  source and the index are proven prefix extra-local copies
  (`ALOAD 3; ASTORE 4; ILOAD 2; ISTORE 5` on `(II[J|[F|[D)V`).
  Computed stores, overwritten copies, prior array stores, and
  computed/`INEG` indexes stay rejected.
  [#263](https://github.com/gaoyu06/native-obfuscator/pull/263) keeps
  extras unassigned on a bridge-taking path fail-closed, including a
  verifier-valid distinct-suffix fixture that reads the extra on only
  one of two bridge-taking paths. No new unassigned-extra shape is
  admitted.
  [#265](https://github.com/gaoyu06/native-obfuscator/pull/265) keeps
  exception tables that span path-id suffixes or cover a chain call
  fail-closed, including a Java 8 cross-suffix fixture and a
  legacy-verifier chain-covering fixture. No new catch-table shape is
  admitted.
  [#266](https://github.com/gaoyu06/native-obfuscator/pull/266) admits
  `ALOAD n; GETFIELD owner.name:desc` when `n` is an unchanged declared
  object argument, `owner` is that exact declared class, and the field
  carrier matches the chain argument.
  [#272](https://github.com/gaoyu06/native-obfuscator/pull/272) also
  admits that leaf when `n` is a proven prefix extra-local copy of such
  a declared object argument.
  [#275](https://github.com/gaoyu06/native-obfuscator/pull/275) covers
  the same extra-local holder for primitive field carriers `I`, `J`,
  `F`, and `D`. `GETFIELD` on local 0, copies of `this`, overwritten
  extra or source holders, computed holders, and mismatched field types
  stay rejected.
  [#267](https://github.com/gaoyu06/native-obfuscator/pull/267) keeps
  nine or more path-id distinct suffixes fail-closed
  (`MAX_DISTINCT_SUFFIXES` remains 8). No new path-count is admitted.
  [#268](https://github.com/gaoyu06/native-obfuscator/pull/268) admits
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V` as a reference
  chain-input leaf when the allocated type exactly matches the call
  argument.
  [#274](https://github.com/gaoyu06/native-obfuscator/pull/274) keeps
  uninitialized `NEW`, `NEW` with initializer arguments, mismatched
  allocation types, and `NEWARRAY`/`ANEWARRAY`/`MULTIANEWARRAY`
  fail-closed, including verifier-valid Java 8 fixtures for the
  loadable shapes. No new allocation form is admitted.
  [#269](https://github.com/gaoyu06/native-obfuscator/pull/269) keeps
  skip-super constructor paths fail-closed, including verifier-valid
  Java 8 fixtures whose ordinary paths construct normally while their
  legal pre-super exceptional paths throw without a chain call. No new
  skip-super shape is admitted.
  [#271](https://github.com/gaoyu06/native-obfuscator/pull/271) keeps
  seventeen-or-more nested int/long/float/double chain-input binaries
  fail-closed. All four family budgets remain 16. No new nested depth
  is admitted.
  Unproven
  prefix→suffix jumps/switches, other mixed try/catch placements
  (tables that span suffixes or cover a chain call), remaining multi-super shapes
  (seventeen-or-more nested int binaries, seventeen-or-more nested long
  binaries, seventeen-or-more nested float binaries,
  seventeen-or-more nested double binaries,
  unproven `NEW` and `GETFIELD` inputs,
  more than eight distinct paths),
  extras still unassigned on a bridge-taking path,
  and skip-super constructors are still
  rejected.
  This is not a JDK 25 support badge and was not re-run as a Temurin 25
  E2E.
- **Classfile versions.** Processed classes keep their input major version.
  Only classes older than Java 8 are raised to the Java 8 floor. Nest, record,
  and `PermittedSubclasses` attributes are no longer dropped by stamping 52.
- **JDK 17 IR runtime repair.** Lookup / class-loader / `link_call_site`
  markers are IR intrinsics. `ObjectMethods.bootstrap` accepts a third
  `TypeDescriptor` parameter. Signature-polymorphic `invoke` / `invokeExact`
  use caller-local trampolines. HotSpot JARs include
  `native0/hidden/Hidden0.class` for reverse-invoke helpers.
- **C++ SDK in generated JARs.** `NativePrimitives` (SHA-256, HMAC-SHA-256,
  AES-256-GCM, constant-time equality) and `NativeStrings` (length, hashCode,
  concat). AES preferred tip includes the 32-bit `plaintext.size+16` overflow
  fix. This is not a separately shipped SDK product.
- **E2E fixtures.** ClassicTest plus JDK 17 / 21 / 25 sample programs under
  `obfuscator/test_data/tests/`. Compiling a fixture with `javac --release 25`
  is not “JDK 25 supported.” The four-fixture IR-mode run in
  [#141](https://github.com/gaoyu06/native-obfuscator/pull/141) is one
  behavioral measurement (20/21 IR, one hybrid constructor, JEP 472 warning),
  not a support badge.
- **Harnesses.** `benchmarks/run.py` now runs JVM, `--codegen=legacy`, and
  `--codegen=ir` in one `:obfuscator:bench` invocation. JNI member-lookup
  caching remains on the legacy path.
- **Opt-in interpreter.** `--backend=interpreter` (default `cpp`) lowers a
  narrow slice to an opcode stream plus a C++17 `switch` dispatcher.
  ISA v2 is static `int`. ISA v3
  ([#140](https://github.com/gaoyu06/native-obfuscator/pull/140); Sol accept
  [#143](https://github.com/gaoyu06/native-obfuscator/pull/143)) adds an i64
  slice. ISA v4
  ([#148](https://github.com/gaoyu06/native-obfuscator/pull/148); Sol accept
  [#149](https://github.com/gaoyu06/native-obfuscator/pull/149)) adds a first
  reference slice (`ACONST_NULL`/`ALOAD`/`ASTORE`/`ARETURN`/`IFNULL`/`IFNONNULL`,
  parallel `jobject` slots, `execute_l`). [#150](https://github.com/gaoyu06/native-obfuscator/pull/150)
  (Sol accept [#151](https://github.com/gaoyu06/native-obfuscator/pull/151))
  adds `ATHROW` (52) and an ordered exception table (typed / catch-all;
  `/0` can transfer to a covering handler). Still static-only.
  `NEW`, invoke, and fields remain outside this backend.
- **Opt-in evaluator.** `--ir-lower=eval` (default `direct`) is consulted only
  when `--codegen=ir` successfully builds an `IrMethod`.
  [#137](https://github.com/gaoyu06/native-obfuscator/pull/137) (Sol
  accept-with-nits [#138](https://github.com/gaoyu06/native-obfuscator/pull/138))
  serializes a narrow i32/i64 slice to a method-data stream plus a C++17
  trampoline. [#139](https://github.com/gaoyu06/native-obfuscator/pull/139)
  (Sol accept [#142](https://github.com/gaoyu06/native-obfuscator/pull/142))
  wires `0x2b`/`0x2c` to phase-20 `LongDivRem`; try/catch around those ops
  still falls back.
  Sources are copied into generated `cpp/` only for this lowering.
- **JEP 472 packaging.** Output JARs now always write
  `Enable-Native-Access: ALL-UNNAMED` unless the input already set that
  attribute ([#145](https://github.com/gaoyu06/native-obfuscator/pull/145);
  Fable accept-with-nits
  [#147](https://github.com/gaoyu06/native-obfuscator/pull/147)). `java -jar`
  honors it; classpath launches still need
  `--enable-native-access=ALL-UNNAMED`. `System.load` is unchanged. This is
  not a JDK 25 support badge, and the #141 E2E warning was not re-run.
- **Zig.** `install-zig` and `--use-zig` from the pre-integration `master`.

默认仍是 `--codegen=legacy`、`--ir-lower=direct` 与 `--backend=cpp`。
IR、evaluator 与解释器都需显式打开。SDK 随生成 JAR 提供，不是独立产品。
classfile 不再无条件写成 major 52。

## Recorded measurements (do not invent more) / 已记录测量（勿编造）

Sources: `docs/benchmarks/ir-admission-phase18-corpus.md`,
`docs/architecture/ir-jdk17-runtime-fix.md`,
`docs/benchmarks/ir-jdk17-e2e-phase17.md`,
`docs/benchmarks/results-ir-eval-lower.md`,
`docs/benchmarks/results-ir-vs-legacy-master.md`,
`docs/benchmarks/results-ir-vs-legacy-phase19.md`,
`docs/benchmarks/ir-jdk17-e2e-corpus.md`,
`docs/benchmarks/ir-jdk25-e2e-corpus.md`.

| Measurement | Result | Must not be read as |
| --- | --- | --- |
| Phase-18 IR admission, ClassicTest corpus | 108 inventory, **108 IR**, 0 fallback | Full JVM coverage or a release gate |
| Phase-18 IR admission, five JDK 17 fixtures | 36 inventory, **36 IR**, 0 fallback | “JDK 17 supported” |
| Phase-18 IR admission, JDK 21 extra | 38 inventory, **36 IR**, 2 fallback (`ISTORE`/`ASTORE` local-type mismatches on `RecordPatternsE2E`) | JDK 21 support |
| IR-mode E2E of those five fixtures on phase 17 (#112) | 5/5 CMake, **0/5** native (crashes) | Anything other than “admission ≠ behavior” |
| Same five fixtures after the runtime repair (#115 / Sol rerun) | 5/5 stdout parity on one Linux x86-64 VM | Product JDK 17 support |
| Expanded JDK 17 IR E2E (#123) | 11/11 stdout parity, 82/82 IR admit, one Linux x86-64 VM (OpenJDK 21 host, `--release 17`) | “JDK 17 supported” |
| JDK 21 IR E2E (#126 via #129) | 6/6 stdout parity, 47/47 IR after local-type split, one Linux VM | “JDK 21 supported” |
| JDK 25 IR E2E (#141; Fable accept-with-nits #144) | 4/4 stdout parity on one Linux VM (host OpenJDK 21.0.10; Temurin 25.0.4.1+1 for compile/oracle/native). **20/21** IR; `FlexibleConstructorBodiesE2E` `Main$Validated.<init>(I)V` left in Java (control flow before `super(...)`, opcode 154). 0 legacy fallbacks. Every transformed run printed the JEP 472 `System::load` restricted-native-access warning. File: `ir-jdk25-e2e-corpus.md` | “JDK 25 supported” |
| Pre-phase-19 bench (#122) | 5 warmup / 10 samples; only `string-concat-hash` stayed fully IR; `integer-loop` LUSHR fallback; `recursion` mixed | Post-phase-19 IR timings. Kept as the pre-phase-19 record |
| Post-phase-19 bench (#132; Fable accept-with-nits #133) | Same harness on `76ebedd`; all three kernels stayed fully IR (four `// IR codegen:` markers; zero fallback log lines). File: `results-ir-vs-legacy-phase19.md` | A portable speedup or “native beats HotSpot” |
| Phase-20 focused tests (#134) | 97 `IrCompilerTest` + 5 `CodegenModeTest` = 102 | A complete compiler test suite |
| Interpreter ISA v4 focused+regression (#148; Sol accept #149) | 128 tests (2 option + 14 emitter + 1 runtime + 2 integration + 102 IR + 7 codegen). Sol re-ran 26/26 (omitted IrCompilerTest). Default-off `diff -r` of generated `cpp/` exited 0 | A production interpreter or object/exception coverage |
| Interpreter exception dispatch (#150; Sol accept #151) | 131 tests (22 interpreter/option + 109 IR/codegen). Sol re-ran 29/29. Runtime harness 61 checks. Default-off `diff -r` exited 0 | Complete catch/finally or instance methods |
| IR `LCMP` (#153; Fable accept #156) | 112 tests (`IrCompilerTest` 105 + `CodegenModeTest` 7). Fable re-ran 112/112. Compiled-and-executed long-compare harness included | Complete IR coverage or a default flip |
| IR `IF_ACMPEQ` / `IF_ACMPNE` (#157) | 114 tests (`IrCompilerTest` 107 + `CodegenModeTest` 7). Parent re-ran 114/114 including `executesReferenceCompareSemanticsWhenToolchainAvailable` | Complete IR coverage or a default flip |
| IR monitors / synchronized (#158) | 118 tests (`IrCompilerTest` 111 + `CodegenModeTest` 7). Parent re-ran 118/118 including `executesMonitorAndSynchronizedSemanticsWhenToolchainAvailable` | Complete IR coverage or a default flip |
| IR `invokedynamic` (#159) | 121 tests (`IrCompilerTest` 114 + `CodegenModeTest` 7). Parent re-ran 121/121 including `executesStringConcatIndyThroughIrWhenToolchainAvailable` | Complete indy/condy coverage or a default flip |
| Constructor prefix parameter `ASTORE` (#160) | 123 tests (`IrCompilerTest` 116 + `CodegenModeTest` 7). Parent re-ran 123/123 including `prefixReferenceParameterAstoreCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| IR proven `ConstantDynamic` + raw MH/MT `LDC` (#161) | 128 tests (`IrCompilerTest` 121 + `CodegenModeTest` 7). Parent re-ran 128/128 including `executesStringConstantDynamicThroughIrWhenToolchainAvailable` and `executesRawMethodTypeLdcThroughIrWhenToolchainAvailable` | Complete condy coverage or a default flip |
| Constructor prefix extra locals (#163) | 133 tests (`IrCompilerTest` 126 + `CodegenModeTest` 7). Parent re-ran 133/133 including `prefixExtraReferenceLocalCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Gapped constructor prefix extras (#164) | 136 tests (`IrCompilerTest` 129 + `CodegenModeTest` 7). Parent re-ran 136/136 including `gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Shared-suffix multi-super diamonds (#165) | 143 tests (`IrCompilerTest` 136 + `CodegenModeTest` 7). Parent re-ran 143/143 including `multipleSuperDiamondCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix-only constructor try/catch (#166) | 148 tests (`IrCompilerTest` 141 + `CodegenModeTest` 7). Parent re-ran 148/148 including `prefixOnlyTryCatchConstructorCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Primitive `Class` `LDC` (#167) | 149 tests (`IrCompilerTest` 142 + `CodegenModeTest` 7). Parent re-ran 149/149 including `primitiveClassLdcCompilesAndRunsWithHotSpotParity` | Complete LDC coverage or a default flip |
| Interface-hosted proven `ConstantDynamic` (#168) | 152 tests (`IrCompilerTest` 145 + `CodegenModeTest` 7). Parent re-ran 152/152 including `interfaceConstantDynamicCompilesAndRunsWithHotSpotParity` | Complete condy coverage or a default flip |
| Identity-preserving constructor `ASTORE 0` (#170) | 155 tests (`IrCompilerTest` 148 + `CodegenModeTest` 7). Parent re-ran 155/155 including `identityAstoreZeroCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated prefix-return mixed constructor catch (#171) | 158 tests (`IrCompilerTest` 151 + `CodegenModeTest` 7). Parent re-ran 158/158 including `relocatedPrefixReturnHandlerCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Identical-suffix multi-super copies (#172) | 162 tests (`IrCompilerTest` 155 + `CodegenModeTest` 7). Parent re-ran 162/162 including `identicalMultiSuperSuffixCopiesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Immediate two-call multi-super returns (#173) | 165 tests (`IrCompilerTest` 158 + `CodegenModeTest` 7). Parent re-ran 165/165 including `immediateMultiSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-chain `IFNE` to shared suffix (#174) | 168 tests (`IrCompilerTest` 161 + `CodegenModeTest` 7). Parent re-ran 168/168 including `postChainConditionalBranchCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Bridge-path-only conditional extras (#175) | 171 tests (`IrCompilerTest` 164 + `CodegenModeTest` 7). Parent re-ran 171/171 including `conditionalBridgeExtraCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| 3+ immediate-return multi-super (#176) | 174 tests (`IrCompilerTest` 167 + `CodegenModeTest` 7). Parent re-ran 174/174 including `threeImmediateSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Remaining post-chain int compares (#177) | 177 tests (`IrCompilerTest` 170 + `CodegenModeTest` 7). Parent re-ran 177/177 including `postChainIntCompareFamiliesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Proven post-chain constructor switches (#178) | 182 tests (`IrCompilerTest` 175 + `CodegenModeTest` 7). Parent re-ran 182/182 including `postChainSwitchesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| 3+ computed/constant chain inputs (#179) | 183 tests (`IrCompilerTest` 176 + `CodegenModeTest` 7). Parent re-ran 183/183 including `threeImmediateSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| 3+ identical nonempty suffixes (#180) | 187 tests (`IrCompilerTest` 180 + `CodegenModeTest` 7). Parent re-ran 187/187 including `threeIdenticalNonemptySuffixCopiesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#180 leftover inventory (#181) | Measurement only on `c99b158`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Leaf-only `IADD` chain inputs (#182) | 187 tests (`IrCompilerTest` 180 + `CodegenModeTest` 7). Parent re-ran 187/187 including `threeImmediateIaddSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `ISUB`/`IMUL` chain inputs (#183) | 190 tests (`IrCompilerTest` 183 + `CodegenModeTest` 7). Parent re-ran 190/190 including `threeImmediateIsubImulSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated `POP; GOTO; RETURN` mixed catch (#184) | 194 tests (`IrCompilerTest` 187 + `CodegenModeTest` 7). Parent re-ran 194/194 including `relocatedPrefixGotoReturnHandlerCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `IAND`/`IOR`/`IXOR` chain inputs (#185) | 197 tests (`IrCompilerTest` 190 + `CodegenModeTest` 7). Parent re-ran 197/197 including `threeImmediateBitwiseSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `ISHL`/`ISHR`/`IUSHR` chain inputs (#186) | 200 tests (`IrCompilerTest` 193 + `CodegenModeTest` 7). Parent re-ran 200/200 including `threeImmediateShiftSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated `ASTORE n; RETURN` mixed catch (#187) | 205 tests (`IrCompilerTest` 198 + `CodegenModeTest` 7). Parent re-ran 205/205 including `relocatedPrefixAstoreReturnHandlerCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated `ATHROW` mixed catch (#188) | 210 tests (`IrCompilerTest` 203 + `CodegenModeTest` 7). Parent re-ran 210/210 including `relocatedPrefixAthrowHandlerCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Two distinct constructor suffixes (#189) | 214 tests (`IrCompilerTest` 207 + `CodegenModeTest` 7). Parent re-ran 214/214 including `twoDifferentSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| 2–8 pairwise-distinct constructor suffixes (#190) | 217 tests (`IrCompilerTest` 210 + `CodegenModeTest` 7). Parent re-ran 217/217 including `threeDistinctSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#190 leftover inventory (#191) | Measurement only on `47e35fc`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extras through distinct suffixes (#192) | 221 tests (`IrCompilerTest` 214 + `CodegenModeTest` 7). Parent re-ran 221/221 including `twoDistinctSuffixesWithExtraCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Closed int branches in distinct suffixes (#193) | 225 tests (`IrCompilerTest` 218 + `CodegenModeTest` 7). Parent re-ran 225/225 including `branchedDistinctSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Closed int branches in 3–8 distinct suffixes (#194) | 229 tests (`IrCompilerTest` 222 + `CodegenModeTest` 7). Parent re-ran 229/229 including `threeBranchedDistinctSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Closed switches in distinct suffixes (#195) | 233 tests (`IrCompilerTest` 226 + `CodegenModeTest` 7). Parent re-ran 233/233 including `suffixSwitchDistinctSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Hybrid identical-plus-distinct suffixes (#196) | 236 tests (`IrCompilerTest` 229 + `CodegenModeTest` 7). Parent re-ran 236/236 including `partlyIdenticalSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Two-level nested chain inputs (#197) | 239 tests (`IrCompilerTest` 232 + `CodegenModeTest` 7). Parent re-ran 239/239 including `threeImmediateNestedInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `IDIV`/`IREM` chain inputs (#198) | 242 tests (`IrCompilerTest` 235 + `CodegenModeTest` 7). Parent re-ran 242/242 including `threeImmediateIdivIremSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#198 leftover inventory (#199) | Measurement only on `4214d74`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Post-#206 leftover inventory (#207) | Measurement only on `42e52c0`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Proven multi-super catch tables (#200) | 249 tests (`IrCompilerTest` 242 + `CodegenModeTest` 7). Parent re-ran 249/249 including `prefixOnlyMultiSuperTryCatchCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Relocated prefix handlers on distinct suffixes (#201) | 252 tests (`IrCompilerTest` 245 + `CodegenModeTest` 7). Parent re-ran 252/252 including `relocatedPrefixHandlerDistinctMultiSuperCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Single-chain receiver-alias forwarding (#202) | 254 tests (`IrCompilerTest` 247 + `CodegenModeTest` 7). Parent re-ran 254/254 including `receiverAliasForwardingCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Shared-join diamond receiver-alias forwarding (#203) | 256 tests (`IrCompilerTest` 249 + `CodegenModeTest` 7). Parent re-ran 256/256 including `receiverAliasMultipleSuperDiamondCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Path-id distinct-suffix receiver-alias forwarding (#204) | 259 tests (`IrCompilerTest` 252 + `CodegenModeTest` 7). Parent re-ran 259/259 including `receiverAliasDistinctSuffixesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Identical-copy receiver-alias forwarding (#205) | 263 tests (`IrCompilerTest` 256 + `CodegenModeTest` 7). Parent re-ran 263/263 including `receiverAliasIdenticalSuffixCopiesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Identical-copy prefix extras (#206) | 267 tests (`IrCompilerTest` 260 + `CodegenModeTest` 7). Parent re-ran 267/267 including `identicalSuffixCopiesWithPrefixExtraCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Identical-copy suffix and relocated catch (#208) | 271 tests (`IrCompilerTest` 264 + `CodegenModeTest` 7). Parent re-ran 271/271 including `identicalSuffixCopiesWithSuffixTryCatchCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated method-end catch handlers (#209) | 275 tests (`IrCompilerTest` 268 + `CodegenModeTest` 7). Parent re-ran 275/275 including `pathIdMethodEndHandlerCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Three-level non-trapping chain inputs (#210) | 278 tests (`IrCompilerTest` 271 + `CodegenModeTest` 7). Parent re-ran 278/278 including `threeImmediateThreeLevelSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| `IDIV`/`IREM` as inner three-level nodes (#211) | 281 tests (`IrCompilerTest` 274 + `CodegenModeTest` 7). Parent re-ran 281/281 including `nestedIdivIremChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Four-level chain-input trees (#212) | 285 tests (`IrCompilerTest` 278 + `CodegenModeTest` 7). Parent re-ran 285/285 including `fourLevelNestedChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `LADD` long chain inputs (#213) | 289 tests (`IrCompilerTest` 282 + `CodegenModeTest` 7). Parent re-ran 289/289 including `threeImmediateLaddSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `LSUB`/`LMUL` long chain inputs (#214) | 292 tests (`IrCompilerTest` 285 + `CodegenModeTest` 7). Parent re-ran 292/292 including `threeImmediateLsubLmulSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `LAND`/`LOR`/`LXOR` long chain inputs (#215) | 295 tests (`IrCompilerTest` 288 + `CodegenModeTest` 7). Parent re-ran 295/295 including `threeImmediateLongBitwiseSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `LSHL`/`LSHR`/`LUSHR` long chain inputs (#216) | 298 tests (`IrCompilerTest` 291 + `CodegenModeTest` 7). Parent re-ran 298/298 including `threeImmediateLongShiftSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `LDIV`/`LREM` long chain inputs (#217) | 301 tests (`IrCompilerTest` 294 + `CodegenModeTest` 7). Parent re-ran 301/301 including `threeImmediateLdivLremSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| One `LNEG` over a declared long `LLOAD` (#218) | 304 tests (`IrCompilerTest` 297 + `CodegenModeTest` 7). Parent re-ran 304/304 including `threeImmediateLnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Two-level nested long chain inputs (#219) | 307 tests (`IrCompilerTest` 300 + `CodegenModeTest` 7). Parent re-ran 307/307 including `twoLevelNestedLongChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Three-level nested long chain inputs (#220) | 310 tests (`IrCompilerTest` 303 + `CodegenModeTest` 7). Parent re-ran 310/310 including `threeLevelNestedLongChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Four-level nested long chain inputs (#221) | 313 tests (`IrCompilerTest` 306 + `CodegenModeTest` 7). Parent re-ran 313/313 including `fourLevelNestedLongChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `FADD` float chain inputs (#222) | 317 tests (`IrCompilerTest` 310 + `CodegenModeTest` 7). Parent re-ran 317/317 including `threeImmediateFaddSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `FSUB`/`FMUL` float chain inputs (#223) | 320 tests (`IrCompilerTest` 313 + `CodegenModeTest` 7). Parent re-ran 320/320 including `threeImmediateFsubFmulSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `FDIV`/`FREM` float chain inputs (#224) | 323 tests (`IrCompilerTest` 316 + `CodegenModeTest` 7). Parent re-ran 323/323 including `threeImmediateFdivFremSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| One `FNEG` over a declared float `FLOAD` (#225) | 326 tests (`IrCompilerTest` 319 + `CodegenModeTest` 7). Parent re-ran 326/326 including `threeImmediateFnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Two-level nested float chain inputs (#226) | 329 tests (`IrCompilerTest` 322 + `CodegenModeTest` 7). Parent re-ran 329/329 including `twoLevelNestedFloatChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Three-level nested float chain inputs (#227) | 332 tests (`IrCompilerTest` 325 + `CodegenModeTest` 7). Parent re-ran 332/332 including `threeLevelNestedFloatChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Four-level nested float chain inputs (#228) | 335 tests (`IrCompilerTest` 328 + `CodegenModeTest` 7). Parent re-ran 335/335 including `fourLevelNestedFloatChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `DADD` double chain inputs (#229) | 339 tests (`IrCompilerTest` 332 + `CodegenModeTest` 7). Parent re-ran 339/339 including `threeImmediateDaddSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `DSUB`/`DMUL` double chain inputs (#230) | 342 tests (`IrCompilerTest` 335 + `CodegenModeTest` 7). Parent re-ran 342/342 including `threeImmediateDsubDmulSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Leaf-only `DDIV`/`DREM` double chain inputs (#231) | 345 tests (`IrCompilerTest` 338 + `CodegenModeTest` 7). Parent re-ran 345/345 including `threeImmediateDdivDremSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| One `DNEG` over a declared double `DLOAD` (#232) | 348 tests (`IrCompilerTest` 341 + `CodegenModeTest` 7). Parent re-ran 348/348 including `threeImmediateDnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Two-level nested double chain inputs (#233) | 351 tests (`IrCompilerTest` 344 + `CodegenModeTest` 7). Parent re-ran 351/351 including `twoLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Three-level nested double chain inputs (#234) | 354 tests (`IrCompilerTest` 347 + `CodegenModeTest` 7). Parent re-ran 354/354 including `threeLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Four-level nested double chain inputs (#235) | 357 tests (`IrCompilerTest` 350 + `CodegenModeTest` 7). Parent re-ran 357/357 including `fourLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local double chain-input leaves (#236) | 360 tests (`IrCompilerTest` 353 + `CodegenModeTest` 7). Parent re-ran 360/360 including `threeImmediateExtraLocalDoubleSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local float chain-input leaves (#237) | 363 tests (`IrCompilerTest` 356 + `CodegenModeTest` 7). Parent re-ran 363/363 including `threeImmediateExtraLocalFloatSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local long chain-input leaves (#238) | 366 tests (`IrCompilerTest` 359 + `CodegenModeTest` 7). Parent re-ran 366/366 including `threeImmediateExtraLocalLongSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local int chain-input leaves (#239) | 370 tests (`IrCompilerTest` 363 + `CodegenModeTest` 7). Parent re-ran 370/370 including `threeImmediateExtraLocalIntSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Five-level nested chain inputs, budget 8 (#240) | 382 tests (`IrCompilerTest` 375 + `CodegenModeTest` 7). Parent re-ran 382/382 including `fiveLevelNestedChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Well-formed `jsr`/`ret` inlining (#241) | 387 tests (`IrCompilerTest` 380 + `CodegenModeTest` 7). Parent re-ran 387/387 including `jsrRetSubroutineCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local long shift values and `LDIV`/`LREM` (#242) | 391 tests (`IrCompilerTest` 384 + `CodegenModeTest` 7). Parent re-ran 391/391 including `threeImmediateExtraLocalLongShiftValueSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local long `LNEG` (#243) | 394 tests (`IrCompilerTest` 387 + `CodegenModeTest` 7). Parent re-ran 394/394 including `threeImmediateExtraLocalLongLnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Retained-prefix `AALOAD` chain inputs (#244) | 397 tests (`IrCompilerTest` 390 + `CodegenModeTest` 7). Parent re-ran 397/397 including `threeImmediateReferenceComputedSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local int long-shift counts (#245) | 400 tests (`IrCompilerTest` 393 + `CodegenModeTest` 7). Parent re-ran 400/400 including `threeImmediateExtraLocalLongShiftCountSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local float `FNEG` (#246) | 403 tests (`IrCompilerTest` 396 + `CodegenModeTest` 7). Parent re-ran 403/403 including `threeImmediateExtraLocalFloatFnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local double `DNEG` (#247) | 406 tests (`IrCompilerTest` 399 + `CodegenModeTest` 7). Parent re-ran 406/406 including `threeImmediateExtraLocalDoubleDnegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Prefix extra-local int `INEG` (#248) | 410 tests (`IrCompilerTest` 403 + `CodegenModeTest` 7). Parent re-ran 410/410 including `threeImmediateExtraLocalIntInegSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Declared-array `IALOAD` int leaves (#249) | 414 tests (`IrCompilerTest` 407 + `CodegenModeTest` 7). Parent re-ran 414/414 including `threeImmediateIntArrayIaloadSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Nine-level nested chain inputs, budget 16 (#250) | 423 tests (`IrCompilerTest` 416 + `CodegenModeTest` 7). Parent re-ran 423/423 including `nineLevelNestedChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local array `AALOAD` sources (#251) | 427 tests (`IrCompilerTest` 420 + `CodegenModeTest` 7). Parent re-ran 427/427 including `threeImmediateExtraLocalArrayAaloadSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local `int[]` `IALOAD` sources (#252) | 430 tests (`IrCompilerTest` 423 + `CodegenModeTest` 7). Parent re-ran 430/430 including `threeImmediateExtraLocalIntArrayIaloadSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Declared-array `BALOAD`/`CALOAD`/`SALOAD` leaves (#253) | 434 tests (`IrCompilerTest` 427 + `CodegenModeTest` 7). Parent re-ran 434/434 including `threeImmediateIntFamilyArrayLoadsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| `AALOAD` declared or extra-local `ILOAD` indexes (#254) | 438 tests (`IrCompilerTest` 431 + `CodegenModeTest` 7). Parent re-ran 438/438 including `threeImmediateExtraLocalIndexAaloadSuperReturnsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| `IALOAD`/`BALOAD`/`CALOAD`/`SALOAD` `ILOAD` indexes (#255) | 441 tests (`IrCompilerTest` 434 + `CodegenModeTest` 7). Parent re-ran 441/441 including `threeImmediateIntArrayLoadIndexesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local `BALOAD`/`CALOAD`/`SALOAD` sources (#256) | 445 tests (`IrCompilerTest` 438 + `CodegenModeTest` 7). Parent re-ran 445/445 including `threeImmediateExtraLocalIntFamilyArrayLoadsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Declared `LALOAD`/`FALOAD`/`DALOAD` leaves (#257) | 449 tests (`IrCompilerTest` 442 + `CodegenModeTest` 7). Parent re-ran 449/449 including `threeImmediateWidePrimitiveArrayLoadsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local `LALOAD`/`FALOAD`/`DALOAD` sources (#258) | 453 tests (`IrCompilerTest` 446 + `CodegenModeTest` 7). Parent re-ran 453/453 including `threeImmediateExtraLocalWidePrimitiveArrayLoadsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| `LALOAD`/`FALOAD`/`DALOAD` `ILOAD` indexes (#259) | 456 tests (`IrCompilerTest` 449 + `CodegenModeTest` 7). Parent re-ran 456/456 including `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-array plus extra-index composition (#260) | 460 tests (`IrCompilerTest` 453 + `CodegenModeTest` 7). Parent re-ran 460/460 including `threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed prefix→suffix crossing audit (#261) | 461 tests (`IrCompilerTest` 454 + `CodegenModeTest` 7). Parent re-ran 461/461 including `unprovenPostChainSwitchShapesPassJava8JvmVerification` | Prefix→suffix leftover remains reject |
| Wide extra-array plus extra-index (#262) | 465 tests (`IrCompilerTest` 458 + `CodegenModeTest` 7). Parent re-ran 465/465 including `threeImmediateWideExtraArrayExtraIndexCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed unassigned-extra audit (#263) | 466 tests (`IrCompilerTest` 459 + `CodegenModeTest` 7). Parent re-ran 466/466 including `unassignedExtraUnusedOnOneDistinctSuffixPassesJvmVerification` | Unassigned-extra leftover remains reject |
| Post-#263 leftover inventory (#264) | Measurement only on `c0304fe`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed spanning-catch audit (#265) | 467 tests (`IrCompilerTest` 460 + `CodegenModeTest` 7). Parent re-ran 467/467 including `spanningAndChainCoveringTryCatchShapesPassJvmVerification` | Spanning/covering catch leftover remains reject |
| Declared-argument `GETFIELD` chain inputs (#266) | 471 tests (`IrCompilerTest` 464 + `CodegenModeTest` 7). Parent re-ran 471/471 including `threeImmediateGetfieldArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed nine-path audit (#267) | 473 tests (`IrCompilerTest` 466 + `CodegenModeTest` 7). Parent re-ran 473/473 including `ninePathIdDistinctSuffixesPassJvmVerification` | Nine-path leftover remains reject |
| Isolated no-arg `NEW` chain inputs (#268) | 477 tests (`IrCompilerTest` 470 + `CodegenModeTest` 7). Parent re-ran 477/477 including `threeImmediateNewArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed skip-super audit (#269) | 478 tests (`IrCompilerTest` 471 + `CodegenModeTest` 7). Parent re-ran 478/478 including `skipSuperConstructorShapesPassJava8JvmVerification` | Skip-super leftover remains reject |
| Post-#269 leftover inventory (#270) | Measurement only on `bca3145`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed seventeen-level audit (#271) | 479 tests (`IrCompilerTest` 472 + `CodegenModeTest` 7). Parent re-ran 479/479 including `seventeenLevelNestedBinariesPassJava8JvmVerification` | Seventeen-level leftover remains reject |
| Extra-local `GETFIELD` holders (#272) | 482 tests (`IrCompilerTest` 475 + `CodegenModeTest` 7). Parent re-ran 482/482 including `threeImmediateGetfieldExtraLocalHolderCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#272 leftover inventory (#273) | Measurement only on `e1b07a8`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed unproven-`NEW` audit (#274) | 483 tests (`IrCompilerTest` 476 + `CodegenModeTest` 7). Parent re-ran 483/483 including `unprovenNewChainInputShapesPassJava8JvmVerification` | Unproven `NEW` leftover remains reject |
| Primitive extra-local `GETFIELD` holders (#275) | 486 tests (`IrCompilerTest` 479 + `CodegenModeTest` 7). Parent re-ran 486/486 including `threeImmediateGetfieldExtraLocalPrimitiveHoldersCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#275 leftover inventory (#276) | Measurement only on `1699fa2`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed unproven-`GETFIELD` audit (#277) | 487 tests (`IrCompilerTest` 480 + `CodegenModeTest` 7). Parent re-ran 487/487 including `unprovenGetfieldChainInputShapesPassJava8JvmVerification` | Unproven `GETFIELD` leftover remains reject |
| Isolated one-arg `NEW` chain inputs (#278) | 490 tests (`IrCompilerTest` 483 + `CodegenModeTest` 7). Parent re-ran 490/490 including `threeImmediateNewOneArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#278 leftover inventory (#279) | Measurement only on `27414d0`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed post-call audit (#280) | 491 tests (`IrCompilerTest` 484 + `CodegenModeTest` 7). Parent re-ran 491/491 including `unprovenPostCallAndAstoreZeroShapesPassJava8JvmVerification` | Post-call leftover remains reject |
| Isolated two-arg `NEW` chain inputs (#281) | 494 tests (`IrCompilerTest` 487 + `CodegenModeTest` 7). Parent re-ran 494/494 including `threeImmediateNewTwoArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#281 leftover inventory (#282) | Measurement only on `c9e4d6e`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Isolated three-arg `NEW` chain inputs (#283) | 497 tests (`IrCompilerTest` 490 + `CodegenModeTest` 7). Parent re-ran 497/497 including `threeImmediateNewThreeArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#283 leftover inventory (#284) | Measurement only on `164fed1`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int-copy `NEW` initializer (#285) | 500 tests (`IrCompilerTest` 493 + `CodegenModeTest` 7). Parent re-ran 500/500 including `threeImmediateNewExtraLocalArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#285 leftover inventory (#286) | Measurement only on `5a9a041`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Isolated four-arg `NEW` chain inputs (#287) | 503 tests (`IrCompilerTest` 496 + `CodegenModeTest` 7). Parent re-ran 503/503 including `threeImmediateNewFourArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed unproven extra-array `AALOAD` audit (#288) | 504 tests (`IrCompilerTest` 497 + `CodegenModeTest` 7). Parent re-ran 504/504 including `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification` | Unproven extra-array `AALOAD` leftover remains reject |
| Post-#288 leftover inventory (#289) | Measurement only on `cdce5a3`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Isolated five-arg `NEW` chain inputs (#290) | 507 tests (`IrCompilerTest` 500 + `CodegenModeTest` 7). Parent re-ran 507/507 including `threeImmediateNewFiveArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Fail-closed constructor `jsr`/`ret` catch audit (#291) | 509 tests (`IrCompilerTest` 502 + `CodegenModeTest` 7). Parent re-ran 509/509 including `unprovenConstructorJsrRetShapesPassJava8JvmVerification` | Constructor `jsr`/`ret` with exception tables remains reject |
| Post-#291 leftover inventory (#292) | Measurement only on `2fbd89d`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Fail-closed unproven wide NEW audit (#293) | 511 tests (`IrCompilerTest` 504 + `CodegenModeTest` 7). Parent re-ran 511/511 including `unprovenWideNewChainInputShapesPassJava8JvmVerification` | Long/float/double NEW initializer leftover remains reject |
| Isolated six-arg `NEW` chain inputs (#294) | 514 tests (`IrCompilerTest` 507 + `CodegenModeTest` 7). Parent re-ran 514/514 including `threeImmediateNewSixArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#294 leftover inventory (#295) | Measurement only on `4459613`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local two-arg `NEW` initializer (#296) | 517 tests (`IrCompilerTest` 510 + `CodegenModeTest` 7). Parent re-ran 517/517 including `threeImmediateNewExtraLocalTwoArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#296 leftover inventory (#297) | Measurement only on `ee8f987`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local three-arg `NEW` initializer (#298) | 520 tests (`IrCompilerTest` 513 + `CodegenModeTest` 7). Parent re-ran 520/520 including `threeImmediateNewExtraLocalThreeArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated long `NEW` chain inputs (#299) | 523 tests (`IrCompilerTest` 516 + `CodegenModeTest` 7). Parent re-ran 523/523 including `threeImmediateNewLongArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local four-arg `NEW` initializer (#300) | 526 tests (`IrCompilerTest` 519 + `CodegenModeTest` 7). Parent re-ran 526/526 including `threeImmediateNewExtraLocalFourArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#299 leftover inventory (#301) | Measurement only on `d070653`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local five-arg `NEW` initializer (#302) | 529 tests (`IrCompilerTest` 522 + `CodegenModeTest` 7). Parent re-ran 529/529 including `threeImmediateNewExtraLocalFiveArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated float `NEW` chain inputs (#303) | 532 tests (`IrCompilerTest` 525 + `CodegenModeTest` 7). Parent re-ran 532/532 including `threeImmediateNewFloatArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local six-arg `NEW` initializer (#304) | 535 tests (`IrCompilerTest` 528 + `CodegenModeTest` 7). Parent re-ran 535/535 including `threeImmediateNewExtraLocalSixArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Isolated double `NEW` chain inputs (#305) | 536 tests (`IrCompilerTest` 529 + `CodegenModeTest` 7). Parent re-ran 536/536 including `threeImmediateNewDoubleArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#304 leftover inventory (#306) | Measurement only on `580ec94`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local long `NEW` initializer (#307) | 539 tests (`IrCompilerTest` 532 + `CodegenModeTest` 7). Parent re-ran 539/539 including `threeImmediateNewExtraLocalLongArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local float `NEW` initializer (#308) | 542 tests (`IrCompilerTest` 535 + `CodegenModeTest` 7). Parent re-ran 542/542 including `threeImmediateNewExtraLocalFloatArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local double `NEW` initializer (#309) | 545 tests (`IrCompilerTest` 538 + `CodegenModeTest` 7). Parent re-ran 545/545 including `threeImmediateNewExtraLocalDoubleArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#309 leftover inventory (#310) | Measurement only on `688c0ea`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local float as second `NEW` initializer (#311) | 548 tests (`IrCompilerTest` 541 + `CodegenModeTest` 7). Parent re-ran 548/548 including `threeImmediateNewExtraLocalFloatSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local double as second `NEW` initializer (#312) | 551 tests (`IrCompilerTest` 544 + `CodegenModeTest` 7). Parent re-ran 551/551 including `threeImmediateNewExtraLocalDoubleSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local float as both `NEW` initializer args (#313) | 554 tests (`IrCompilerTest` 547 + `CodegenModeTest` 7). Parent re-ran 554/554 including `threeImmediateNewExtraLocalFloatBothArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local double as both `NEW` initializer args (#314) | 557 tests (`IrCompilerTest` 550 + `CodegenModeTest` 7). Parent re-ran 557/557 including `threeImmediateNewExtraLocalDoubleBothArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#314 leftover inventory (#315) | Measurement only on `434c489`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second two-arg `NEW` initializer (#316) | 560 tests (`IrCompilerTest` 553 + `CodegenModeTest` 7). Parent re-ran 560/560 including `threeImmediateNewExtraLocalTwoSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local int as both two-arg `NEW` initializer args (#317) | 563 tests (`IrCompilerTest` 556 + `CodegenModeTest` 7). Parent re-ran 563/563 including `threeImmediateNewExtraLocalTwoBothArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#317 leftover inventory (#318) | Measurement only on `b35fa0b`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second three-arg `NEW` initializer (#319) | 566 tests (`IrCompilerTest` 559 + `CodegenModeTest` 7). Parent re-ran 566/566 including `threeImmediateNewExtraLocalThreeSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local int as third three-arg `NEW` initializer (#320) | 569 tests (`IrCompilerTest` 562 + `CodegenModeTest` 7). Parent re-ran 569/569 including `threeImmediateNewExtraLocalThreeThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#320 leftover inventory (#321) | Measurement only on `73c279e`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as all three Color `NEW` initializer args (#322) | 572 tests (`IrCompilerTest` 565 + `CodegenModeTest` 7). Parent re-ran 572/572 including `threeImmediateNewExtraLocalThreeAllArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#322 leftover inventory (#323) | Measurement only on `ae1b8da`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first and second Color `NEW` args (#324) | 575 tests (`IrCompilerTest` 568 + `CodegenModeTest` 7). Parent re-ran 575/575 including `threeImmediateNewExtraLocalThreeFirstSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#324 leftover inventory (#325) | Measurement only on `4f8612a`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first and third Color `NEW` args (#326) | 578 tests (`IrCompilerTest` 571 + `CodegenModeTest` 7). Parent re-ran 578/578 including `threeImmediateNewExtraLocalThreeFirstThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#326 leftover inventory (#327) | Measurement only on `0894b16`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second and third Color `NEW` args (#328) | 581 tests (`IrCompilerTest` 574 + `CodegenModeTest` 7). Parent re-ran 581/581 including `threeImmediateNewExtraLocalThreeSecondThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#328 leftover inventory (#329) | Measurement only on `b751add`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second Insets `NEW` arg (#330) | 584 tests (`IrCompilerTest` 577 + `CodegenModeTest` 7). Parent re-ran 584/584 including `threeImmediateNewExtraLocalFourSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#330 leftover inventory (#331) | Measurement only on `753c401`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as third Insets `NEW` arg (#332) | 587 tests (`IrCompilerTest` 580 + `CodegenModeTest` 7). Parent re-ran 587/587 including `threeImmediateNewExtraLocalFourThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#332 leftover inventory (#333) | Measurement only on `c4b6461`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as fourth Insets `NEW` arg (#334) | 590 tests (`IrCompilerTest` 583 + `CodegenModeTest` 7). Parent re-ran 590/590 including `threeImmediateNewExtraLocalFourFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#334 leftover inventory (#335) | Measurement only on `c99c0f9`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as all four Insets `NEW` args (#336) | 593 tests (`IrCompilerTest` 586 + `CodegenModeTest` 7). Parent re-ran 593/593 including `threeImmediateNewExtraLocalFourAllArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#336 leftover inventory (#337) | Measurement only on `d5faac9`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first and second Insets `NEW` args (#338) | 596 tests (`IrCompilerTest` 589 + `CodegenModeTest` 7). Parent re-ran 596/596 including `threeImmediateNewExtraLocalFourFirstSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#338 leftover inventory (#339) | Measurement only on `f7446f5`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first and third Insets `NEW` args (#340) | 599 tests (`IrCompilerTest` 592 + `CodegenModeTest` 7). Parent re-ran 599/599 including `threeImmediateNewExtraLocalFourFirstThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#340 leftover inventory (#341) | Measurement only on `b4ec2a7`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first and fourth Insets `NEW` args (#342) | 602 tests (`IrCompilerTest` 595 + `CodegenModeTest` 7). Parent re-ran 602/602 including `threeImmediateNewExtraLocalFourFirstFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#342 leftover inventory (#343) | Measurement only on `6d7b342`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second and third Insets `NEW` args (#344) | 605 tests (`IrCompilerTest` 598 + `CodegenModeTest` 7). Parent re-ran 605/605 including `threeImmediateNewExtraLocalFourSecondThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#344 leftover inventory (#345) | Measurement only on `39f1758`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second and fourth Insets `NEW` args (#346) | 608 tests (`IrCompilerTest` 601 + `CodegenModeTest` 7). Parent re-ran 608/608 including `threeImmediateNewExtraLocalFourSecondFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#346 leftover inventory (#347) | Measurement only on `d842255`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as third and fourth Insets `NEW` args (#348) | 611 tests (`IrCompilerTest` 604 + `CodegenModeTest` 7). Parent re-ran 611/611 including `threeImmediateNewExtraLocalFourThirdFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#348 leftover inventory (#349) | Measurement only on `94a4e0e`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first, second, and third Insets `NEW` args (#350) | 614 tests (`IrCompilerTest` 607 + `CodegenModeTest` 7). Parent re-ran 614/614 including `threeImmediateNewExtraLocalFourFirstSecondThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#350 leftover inventory (#351) | Measurement only on `2246b1c`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first, second, and fourth Insets `NEW` args (#352) | 617 tests (`IrCompilerTest` 610 + `CodegenModeTest` 7). Parent re-ran 617/617 including `threeImmediateNewExtraLocalFourFirstSecondFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#352 leftover inventory (#353) | Measurement only on `764ecf5`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as first, third, and fourth Insets `NEW` args (#354) | 620 tests (`IrCompilerTest` 613 + `CodegenModeTest` 7). Parent re-ran 620/620 including `threeImmediateNewExtraLocalFourFirstThirdFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#354 leftover inventory (#355) | Measurement only on `b8478cc`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as second, third, and fourth Insets `NEW` args (#356) | 623 tests (`IrCompilerTest` 616 + `CodegenModeTest` 7). Parent re-ran 623/623 including `threeImmediateNewExtraLocalFourSecondThirdFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#356 leftover inventory (#357) | Measurement only on `6efe734`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the second five-arg GregorianCalendar `NEW` arg (#358) | 626 tests (`IrCompilerTest` 619 + `CodegenModeTest` 7). Parent re-ran 626/626 including `threeImmediateNewExtraLocalFiveSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#358 leftover inventory (#359) | Measurement only on `49b2e8c`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the third five-arg GregorianCalendar `NEW` arg (#360) | 629 tests (`IrCompilerTest` 622 + `CodegenModeTest` 7). Parent re-ran 629/629 including `threeImmediateNewExtraLocalFiveThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#360 leftover inventory (#361) | Measurement only on `7feb4c0`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the fourth five-arg GregorianCalendar `NEW` arg (#362) | 632 tests (`IrCompilerTest` 625 + `CodegenModeTest` 7). Parent re-ran 632/632 including `threeImmediateNewExtraLocalFiveFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#362 leftover inventory (#363) | Measurement only on `3de4bc2`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the fifth five-arg GregorianCalendar `NEW` arg (#364) | 635 tests (`IrCompilerTest` 628 + `CodegenModeTest` 7). Parent re-ran 635/635 including `threeImmediateNewExtraLocalFiveFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#364 leftover inventory (#365) | Measurement only on `de6d4d6`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as all five five-arg GregorianCalendar `NEW` args (#366) | 638 tests (`IrCompilerTest` 631 + `CodegenModeTest` 7). Parent re-ran 638/638 including `threeImmediateNewExtraLocalFiveAllArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#366 leftover inventory (#367) | Measurement only on `45e6a51`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first and second five-arg GregorianCalendar `NEW` args (#368) | 641 tests (`IrCompilerTest` 634 + `CodegenModeTest` 7). Parent re-ran 641/641 including `threeImmediateNewExtraLocalFiveFirstSecondArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#368 leftover inventory (#369) | Measurement only on `215397a`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first and third five-arg GregorianCalendar `NEW` args (#370) | 644 tests (`IrCompilerTest` 637 + `CodegenModeTest` 7). Parent re-ran 644/644 including `threeImmediateNewExtraLocalFiveFirstThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#370 leftover inventory (#371) | Measurement only on `28f3f15`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first and fourth five-arg GregorianCalendar `NEW` args (#372) | 647 tests (`IrCompilerTest` 640 + `CodegenModeTest` 7). Parent re-ran 647/647 including `threeImmediateNewExtraLocalFiveFirstFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local int as the first and fifth five-arg GregorianCalendar `NEW` args (#373) | 650 tests (`IrCompilerTest` 643 + `CodegenModeTest` 7). Parent re-ran 650/650 including `threeImmediateNewExtraLocalFiveFirstFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#372 leftover inventory (#374) | Measurement only on `80ed2e0`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the second and third five-arg GregorianCalendar `NEW` args (#375) | 653 tests (`IrCompilerTest` 646 + `CodegenModeTest` 7). Parent re-ran 653/653 including `threeImmediateNewExtraLocalFiveSecondThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#375 leftover inventory (#376) | Measurement only on `1b48c85`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the second and fourth five-arg GregorianCalendar `NEW` args (#377) | 656 tests (`IrCompilerTest` 649 + `CodegenModeTest` 7). Parent re-ran 656/656 including `threeImmediateNewExtraLocalFiveSecondFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#377 leftover inventory (#378) | Measurement only on `de3f430`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the second and fifth five-arg GregorianCalendar `NEW` args (#379) | 659 tests (`IrCompilerTest` 652 + `CodegenModeTest` 7). Parent re-ran 659/659 including `threeImmediateNewExtraLocalFiveSecondFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Extra-local int as the third and fourth five-arg GregorianCalendar `NEW` args (#380) | 662 tests (`IrCompilerTest` 655 + `CodegenModeTest` 7). Parent re-ran 662/662 including `threeImmediateNewExtraLocalFiveThirdFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#379 leftover inventory (#381) | Measurement only on `9fa5181`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the third and fifth five-arg GregorianCalendar `NEW` args (#382) | 665 tests (`IrCompilerTest` 658 + `CodegenModeTest` 7). Parent re-ran 665/665 including `threeImmediateNewExtraLocalFiveThirdFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#381 leftover inventory (#383) | Measurement only on `109c318`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the fourth and fifth five-arg GregorianCalendar `NEW` args (#384) | 668 tests (`IrCompilerTest` 661 + `CodegenModeTest` 7). Parent re-ran 668/668 including `threeImmediateNewExtraLocalFiveFourthFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#383 leftover inventory (#385) | Measurement only on `6649865`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first, second, and third five-arg GregorianCalendar `NEW` args (#386) | 671 tests (`IrCompilerTest` 664 + `CodegenModeTest` 7). Parent re-ran 671/671 including `threeImmediateNewExtraLocalFiveFirstSecondThirdArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#385 leftover inventory (#387) | Measurement only on `48f5ab5`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first, second, and fourth five-arg GregorianCalendar `NEW` args (#388) | 674 tests (`IrCompilerTest` 667 + `CodegenModeTest` 7). Parent re-ran 674/674 including `threeImmediateNewExtraLocalFiveFirstSecondFourthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#387 leftover inventory (#389) | Measurement only on `3b9fce8`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Extra-local int as the first, second, and fifth five-arg GregorianCalendar `NEW` args (#390) | 677 tests (`IrCompilerTest` 670 + `CodegenModeTest` 7). Parent re-ran 677/677 including `threeImmediateNewExtraLocalFiveFirstSecondFifthArgChainInputsCompileAndRunWithJavaParity` | Remaining ctor-split rejects are gone |
| Post-#389 leftover inventory (#391) | Measurement only on `1db7af5`: ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21 IR. 0 leftovers | Not coverage-complete; not a JDK support badge |
| Phase-18 focused tests (Sol + Fable) | 88 `IrCompilerTest` + 4 `CodegenModeTest` = 92 | A complete compiler test suite |
| Runtime-fix focused tests (Sol / Fable on #115) | 85 + 4 = 89 before later phase-18 tests were stacked | — |
| #53 eval-lower bench | Eval fell back; median **N/A** | Do not back-fill |
| Reader evals | `add` / `sumTo` / `subMul` / `mix` recovered on live IR and opcode artifacts | A passed “resist Sol-class recovery” bar |

Admission 不是行为正确性。五个 fixture 的 5/5 只是一台 Linux VM 上的记录。

## What did not land as compiler code / 未作为编译器代码落地

Old sibling evaluator PRs #42–#87 **conflict with the phase-18 + #124 line**
and must not be merged. The current-master port is #137 (landed). Their
reader/bench notes remain historical. Open drafts #9–#117 and #121 are the
pre-#118 stacked tips; merging them onto current `master` would regress the
tree. Close them as superseded, do not merge.

- Older opcode interpreter / compact encoding / link-only output, PRs #17–#28
  (superseded as a *first increment* by #124; those sibling flags are still
  not the current CLI)
- Standalone NativeStrings-on-master #27 (superseded by the SDK stack)

不要把旧 #42–#87 或 #17–#28 的 CLI 旗标当成当前功能。

## Defaults and policies that remain / 仍然有效的默认与政策

1. Do not flip `--codegen` off `legacy` (or `--ir-lower` off `direct`)
   until IR no longer needs per-method snippet fallback for the methods
   the product intends to support. The approved *destination* is then to
   flip the default to `ir` and delete the legacy path (D7).
2. Do not publish “supports JDK 17/21/25” from admission counts or five fixtures.
3. Do not claim a general native speedup versus HotSpot.
4. The old requirement-7 reader bar is historical and unmet. Do not
   launch another encoding-tweak reader. It is not the active goal.
5. Keep #53’s eval median as `N/A`.
6. Option A in older briefs is historical. The active goal is
   [current-goal.md](current-goal.md), not the eight-requirement write-up.

在 IR 覆盖完成前不要改默认 `legacy`。不要用接纳率或五个用例宣称 JDK
支持。#53 的 eval 中位数保持 `N/A`。现行目标尚未完成。

## Suggested next engineering / 后续工程

Active-goal work (IR admission, then default flip, then legacy deletion):

- Admit remaining IR leftovers so methods stop falling back:
  leftover constructor-split rejects (unproven
  prefix→suffix jumps/switches, other mixed try/catch placements
  beyond #171/#184/#187/#188/#200/#201/#208/#209 such as tables that span
  suffixes or cover a chain call,
  remaining multi-super shapes such as seventeen-or-more nested
  int binaries, seventeen-or-more nested long
  binaries, seventeen-or-more nested float binaries,
  seventeen-or-more nested double binaries,
  extras still unassigned
  on a bridge-taking path,
  unproven `NEW` and `GETFIELD` inputs,
  unproven extra-array `AALOAD` inputs,
  more than eight distinct paths,
  skip-super constructors,
  post-call extra work and three-immediate `astore-zero`,
  constructor `jsr`/`ret` with an exception table).
  Malformed `jsr`/`ret` stay reject-before-mutation; well-formed
  straight-line subroutines are admitted by #241. Constructor `jsr`/`ret`
  with an exception table or non-straight-line inlined clone stay
  reject-before-mutation; #291 strengthens those fail-closed tests. Extra-local long shift values and
  `LDIV`/`LREM` operands are admitted by #242. Extra-local long `LNEG`
  is admitted by #243. Retained-prefix `AALOAD` chain inputs are
  admitted by #244. Extra-local int long-shift counts are admitted
  by #245. Extra-local float `FNEG` is admitted by #246.
  Extra-local double `DNEG` is admitted by #247.
  Extra-local int `INEG` is admitted by #248.
  Declared-array `IALOAD` int leaves are admitted by #249.
  Nine-level nested binaries are admitted by #250 (budget 16).
  Extra-local array `AALOAD` sources are admitted by #251.
  Extra-local `int[]` `IALOAD` sources are admitted by #252.
  Declared-array `BALOAD`/`CALOAD`/`SALOAD` leaves are admitted by #253.
  `AALOAD` declared or extra-local `ILOAD` indexes are admitted by #254.
  `IALOAD`/`BALOAD`/`CALOAD`/`SALOAD` `ILOAD` indexes are admitted by #255.
  Extra-local `BALOAD`/`CALOAD`/`SALOAD` sources are admitted by #256.
  Declared `LALOAD`/`FALOAD`/`DALOAD` leaves are admitted by #257.
  Extra-local `LALOAD`/`FALOAD`/`DALOAD` sources are admitted by #258.
  `LALOAD`/`FALOAD`/`DALOAD` declared or extra-local `ILOAD` indexes are
  admitted by #259.
  Extra-array plus extra-index composition for `AALOAD` and int-family
  loads is admitted by #260.
  Unproven prefix→suffix crossings stay reject-before-mutation; #261
  strengthens those fail-closed tests.
  Wide extra-array plus extra-index composition for `LALOAD`, `FALOAD`,
  and `DALOAD` is admitted by #262.
  Unassigned extras on a bridge-taking path stay reject-before-mutation;
  #263 strengthens those fail-closed tests.
  Tables that span suffixes or cover a chain call stay
  reject-before-mutation; #265 strengthens those fail-closed tests.
  Declared-argument `GETFIELD` chain inputs are admitted by #266.
  Extra-local proven object-copy `GETFIELD` holders are admitted by #272.
  Primitive-carrier extra-local `GETFIELD` holders are admitted by #275.
  Isolated no-arg `NEW` chain inputs are admitted by #268.
  Isolated one-arg int-family `NEW` chain inputs are admitted by #278.
  Isolated two-arg int-family `NEW` chain inputs are admitted by #281.
  Isolated three-arg int-family `NEW` chain inputs are admitted by #283.
  Extra-local proven int-copy `NEW` initializer arguments are admitted
  by #285 (fixture-only). Extra-local proven int-copy as a two-arg
  `NEW` initializer is admitted by #296 (fixture-only). Extra-local
  proven int-copy as a three-arg `NEW` initializer is admitted by
  #298 (fixture-only).   Extra-local proven int-copy as a four-arg
  `NEW` initializer is admitted by #300 (fixture-only). Extra-local
  proven int-copy as a five-arg `NEW` initializer is admitted by
  #302 (fixture-only). Extra-local proven int-copy as a six-arg
  `NEW` initializer is admitted by #304 (fixture-only).
  Isolated four-arg int-family `NEW` chain inputs are admitted by #287.
  Isolated five-arg int-family `NEW` chain inputs are admitted by #290.
  Isolated six-arg int-family `NEW` chain inputs are admitted by #294.
  Isolated one-arg long `NEW` chain inputs are admitted by #299.
  Extra-local proven long-copy `NEW` initializer arguments are admitted
  by #307 (fixture-only). Extra-local proven float-copy `NEW`
  initializer arguments are admitted by #308 (fixture-only).
  Extra-local proven double-copy `NEW` initializer arguments are admitted
  by #309 (fixture-only). Extra-local proven float-copy as the second
  `NEW` initializer argument is admitted by #311 (fixture-only).
  Extra-local proven double-copy as the second `NEW` initializer
  argument is admitted by #312 (fixture-only). Extra-local proven
  float-copy as both `NEW` initializer arguments is admitted by
  #313 (fixture-only). Extra-local proven double-copy as both
  `NEW` initializer arguments is admitted by #314 (fixture-only).
  Extra-local proven int-copy as the second two-arg `NEW`
  initializer argument is admitted by #316 (fixture-only).
  Extra-local proven int-copy as both two-arg `NEW`
  initializer arguments is admitted by #317 (fixture-only).
  Extra-local proven int-copy as the second three-arg `NEW`
  initializer argument is admitted by #319 (fixture-only).
  Extra-local proven int-copy as the third three-arg `NEW`
  initializer argument is admitted by #320 (fixture-only).
  Extra-local proven int-copy as all three Color `NEW`
  initializer arguments is admitted by #322 (fixture-only).
  Extra-local proven int-copy as the first and second Color `NEW`
  initializer arguments is admitted by #324 (fixture-only).
  Extra-local proven int-copy as the first and third Color `NEW`
  initializer arguments is admitted by #326 (fixture-only).
  Extra-local proven int-copy as the second and third Color `NEW`
  initializer arguments is admitted by #328 (fixture-only).
  Extra-local proven int-copy as the second four-arg Insets `NEW`
  initializer argument is admitted by #330 (fixture-only).
  Extra-local proven int-copy as the third four-arg Insets `NEW`
  initializer argument is admitted by #332 (fixture-only).
  Extra-local proven int-copy as the fourth four-arg Insets `NEW`
  initializer argument is admitted by #334 (fixture-only).
  Extra-local proven int-copy as all four Insets `NEW`
  initializer arguments is admitted by #336 (fixture-only).
  Extra-local proven int-copy as the first and second Insets `NEW`
  initializer arguments is admitted by #338 (fixture-only).
  Extra-local proven int-copy as the first and third Insets `NEW`
  initializer arguments is admitted by #340 (fixture-only).
  Extra-local proven int-copy as the first and fourth Insets `NEW`
  initializer arguments is admitted by #342 (fixture-only).
  Extra-local proven int-copy as the second and third Insets `NEW`
  initializer arguments is admitted by #344 (fixture-only).
  Extra-local proven int-copy as the second and fourth Insets `NEW`
  initializer arguments is admitted by #346 (fixture-only).
  Extra-local proven int-copy as the third and fourth Insets `NEW`
  initializer arguments is admitted by #348 (fixture-only).
  Extra-local proven int-copy as the first, second, and third Insets `NEW`
  initializer arguments is admitted by #350 (fixture-only).
  Extra-local proven int-copy as the first, second, and fourth Insets `NEW`
  initializer arguments is admitted by #352 (fixture-only).
  Extra-local proven int-copy as the first, third, and fourth Insets `NEW`
  initializer arguments is admitted by #354 (fixture-only).
  Extra-local proven int-copy as the second, third, and fourth Insets `NEW`
  initializer arguments is admitted by #356 (fixture-only).
  Extra-local proven int-copy as the second five-arg GregorianCalendar `NEW`
  initializer argument is admitted by #358 (fixture-only).
  Extra-local proven int-copy as the third five-arg GregorianCalendar `NEW`
  initializer argument is admitted by #360 (fixture-only).
  Extra-local proven int-copy as the fourth five-arg GregorianCalendar `NEW`
  initializer argument is admitted by #362 (fixture-only).
  Extra-local proven int-copy as the fifth five-arg GregorianCalendar `NEW`
  initializer argument is admitted by #364 (fixture-only).
  Extra-local proven int-copy as all five five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #366 (fixture-only).
  Extra-local proven int-copy as the first and second five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #368 (fixture-only).
  Extra-local proven int-copy as the first and third five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #370 (fixture-only).
  Extra-local proven int-copy as the first and fourth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #372 (fixture-only).
  Extra-local proven int-copy as the first and fifth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #373 (fixture-only).
  Extra-local proven int-copy as the second and third five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #375 (fixture-only).
  Extra-local proven int-copy as the second and fourth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #377 (fixture-only).
  Extra-local proven int-copy as the second and fifth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #379 (fixture-only).
  Extra-local proven int-copy as the third and fourth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #380 (fixture-only).
  Extra-local proven int-copy as the third and fifth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #382 (fixture-only).
  Extra-local proven int-copy as the fourth and fifth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #384 (fixture-only).
  Extra-local proven int-copy as the first, second, and third five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #386 (fixture-only).
  Extra-local proven int-copy as the first, second, and fourth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #388 (fixture-only).
  Extra-local proven int-copy as the first, second, and fifth five-arg GregorianCalendar `NEW`
  initializer arguments is admitted by #390 (fixture-only).
  Isolated float `NEW` chain inputs are admitted by #303.
  Isolated double `NEW` chain inputs are admitted by #305.
  Unproven extra-array `AALOAD` forms stay reject-before-mutation; #288
  strengthens those fail-closed tests. Do not admit computed/`INEG`
  extra-array stores, overwritten extras, prior array stores, or a
  primitive array presented as a reference `AALOAD` result.
  Unproven `NEW` forms stay reject-before-mutation; #274
  strengthens those fail-closed tests. Do not admit `NEW` with
  seven or more initializer arguments, array-allocation opcodes,
  extra-local of `this`, or overwritten / computed extras as
  initializer arguments.   Isolated float initializer arguments are
  admitted by #303. Isolated double initializer arguments are
  admitted by #305. Isolated long
  initializer arguments are admitted by #299.
  Unproven `GETFIELD` forms stay reject-before-mutation; #277
  strengthens those fail-closed tests.
  More than eight path-id suffixes stay reject-before-mutation; #267
  strengthens those fail-closed tests. The eight-path cap is unchanged.
  Skip-super constructors stay reject-before-mutation; #269
  strengthens those fail-closed tests. Do not admit skip-super.
  Extra work after a chain call (`post-call`) and three-immediate
  `astore-zero` stay reject-before-mutation; #280 strengthens those
  fail-closed tests. Do not admit post-call extra work. Identity
  `ASTORE 0` remains the existing admit.
  Constructor `jsr`/`ret` with an exception table or a non-straight-line
  inlined clone stay reject-before-mutation; #291 strengthens those
  fail-closed tests. Do not admit those leftovers. The admitted
  straight-line no-exception-table constructor prefix remains #241.
  Seventeen-or-more nested binaries stay reject-before-mutation; #271
  strengthens those fail-closed tests. The sixteen-level family budgets
  are unchanged. Do not admit unbounded depth.
  Remaining unsafe condy shapes stay fail-closed. In-tree fixture admission
  ([#391](https://github.com/gaoyu06/native-obfuscator/pull/391),
  measured on leftover-docs #389 `1db7af5`) observed 0 leftovers; that is not
  coverage-complete. #389 remains the earlier leftover-docs #387 snapshot.
  #387 remains the earlier leftover-docs #385 snapshot.
  #385 remains the earlier leftover-docs #383 snapshot.
  #383 remains the earlier leftover-docs #381 snapshot.
  #381 remains the earlier post-#379 snapshot.
  #378 remains the earlier post-#377 snapshot.
  #376 remains the earlier post-#375 snapshot.
  #374 remains the earlier post-#372 snapshot.
  #371 remains the earlier post-#370 snapshot.
  #369 remains the earlier post-#368 snapshot.
  #367 remains the earlier post-#366 snapshot.
  #365 remains the earlier post-#364 snapshot.
  #363 remains the earlier post-#362 snapshot.
  #361 remains the earlier post-#360 snapshot.
  #359 remains the earlier post-#358 snapshot.
  #357 remains the earlier post-#356 snapshot.
  #355 remains the earlier post-#354 snapshot.
  #353 remains the earlier post-#352 snapshot.
  #351 remains the earlier post-#350 snapshot.
  #349 remains the earlier post-#348 snapshot.
  #347 remains the earlier post-#346 snapshot.
  #345 remains the earlier post-#344 snapshot.
  #343 remains the earlier post-#342 snapshot.
  #341 remains the earlier post-#340 snapshot.
  #339 remains the earlier post-#338 snapshot.
  #337 remains the earlier post-#336 snapshot.
  #335 remains the earlier post-#334 snapshot.
  #333 remains the earlier post-#332 snapshot.
  #331 remains the earlier post-#330 snapshot.
  #329 remains the earlier post-#328 snapshot.
  #327 remains the earlier post-#326 snapshot.
  #325 remains the earlier post-#324 snapshot.
  #323 remains the earlier post-#322 snapshot.
  #321 remains the earlier post-#320 snapshot.
  #318 remains the earlier post-#317 snapshot.
  #315 remains the earlier post-#314 snapshot.
  #310 remains the earlier post-#309 snapshot.
  #306 remains the earlier post-#304 snapshot.
  #301 remains the earlier post-#299 snapshot.
  #297 remains the earlier post-#296 snapshot.
  #295 remains the earlier post-#294 snapshot.
  #292 remains the earlier post-#291 snapshot.
  #289 remains the earlier post-#288 snapshot.
  #286 remains the earlier post-#285 snapshot.
  #284 remains the earlier post-#283 snapshot.
  #282 remains the earlier post-#281 snapshot.
  #279 remains the earlier post-#278 snapshot.
  #276 remains the earlier post-#275 snapshot.
  #273 remains the earlier post-#272 snapshot.
  #270 remains the earlier post-#269 snapshot.
  #264 remains the earlier post-#263 snapshot.
  #207 remains the earlier post-#206 snapshot.
  #199 remains the earlier post-#198 snapshot.
  #191 remains the earlier post-#190 snapshot.
- After coverage: reversible `--codegen` default flip to `ir`, soak,
  then delete `Snippets` / `cppsnippets.properties` / string-concat
  handlers.

Not a substitute for the active goal:

- Interpreter and evaluator remain default-off side paths.
- Human decisions in `human-decision-matrix.md` before any support badge.

## (a)(b)(c)(d) for this document / 本文发布问答

- **(a) Scope / 范围:** Status refresh after landing #391
  (leftover inventory remasurement on leftover-docs #389). /
  落地 #391 之后的现状刷新。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review / 是否需要审查？** Yes — check that no support badge
  leaked and that the CLI default was not flipped. /
  是，确认 README 没有写成产品支持，也没有改掉默认值。
- **(d) Preconditions / 前置条件:** Cite only committed measurement
  files; do not mark the new goal complete. /
  只引用已提交的测量文件；不要把新目标标成完成。
