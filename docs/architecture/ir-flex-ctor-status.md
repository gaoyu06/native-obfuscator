# Flexible constructor split status

## Scope

`ConstructorSpecialMethodProcessor.split()` decides whether a constructor can be
split into a retained bytecode part (the uninitialized-this prefix plus the
verifier-required this/super `INVOKESPECIAL <init>` call) and an initialized-this
suffix that is compiled by the IR frontend and reached through a hidden static
native bridge.

The constructor split now covers these related prefix shapes:

- branches and switches that remain entirely in the retained prefix;
- try/catch entries whose start, end, and handler labels all remain in the
  retained prefix;
- suffix-protected try/catch entries targeting an isolated prefix handler that
  consumes the caught exception with `POP` or stores it in a safe non-receiver
  reference local with `ASTORE`, then either returns directly or uses a sole
  `GOTO` edge to an isolated prefix `RETURN`, plus exact `ATHROW` and
  `ASTORE n; ALOAD n; ATHROW` handlers that rethrow the caught exception, for
  both single-super and bounded path-id distinct-suffix constructors;
- identity-preserving `ASTORE 0` writes, plus receiver-alias forwarding for a
  single call, a strict `GOTO` shared-join diamond (including one produced by
  identical-copy normalization), or bounded path-id distinct suffixes where
  the original receiver is saved in another local before local 0 receives a
  different reference and every selected this/super call is proven to consume
  the saved receiver;
- `ASTORE` updates to reference/array constructor-parameter slots, with only
  those bridge parameters widened to `java/lang/Object`;
- extra reference or primitive locals written in the prefix and read in the
  suffix, when their incoming state at the hidden bridge is provable;
- multiple direct this/super calls in a strict diamond that converges at one
  shared suffix label;
- exactly two direct this/super calls where the first call is followed by one
  or two declared int-family argument `ILOAD`s, a unary int-zero or binary
  `IF_ICMPxx` jump to the shared suffix, and immediate fallthrough `RETURN`,
  while the second call falls through to that suffix; and
- exactly two direct this/super calls where the first call is followed by one
  declared int-family argument `ILOAD` and `TABLESWITCH` or `LOOKUPSWITCH`,
  every target is the shared suffix, an immediate `RETURN`, or a direct `GOTO`
  to that suffix, and the second call falls through to the suffix; and
- exactly two direct this/super calls where the first call returns immediately
  and the second falls through to the suffix, only when a forwarded extra local
  is unassigned at the exiting call but has one provable type on every path
  that reaches the hidden bridge; and
- exactly two direct this/super calls whose separate straight-line suffix
  copies, including immediate `RETURN` copies, are proven
  instruction-for-instruction identical; and
- prefix-assigned extra locals read by identical straight-line suffix copies,
  including the saved receiver alias, when one compatible type is proven at
  every chain call and again at the normalized join; and
- exception tables whose protected range and handler are wholly in the
  canonical final identical suffix copy, plus canonical-suffix ranges targeting
  one of the proven isolated prefix handlers or one proven isolated handler
  tail immediately after the canonical suffix; and
- between two and eight reachable direct this/super calls whose separate
  nonempty suffixes end in `RETURN` and contain at least two CFG-distinct
  ranges, when every call consumes the original receiver through direct
  `ALOAD 0` or the proven prefix alias and uses only locally proven chain
  arguments; repeated suffix CFGs remain separate path-id ranges, and any
  suffix may additionally contain one closed int-family
  conditional branch, or one closed
  `TABLESWITCH`/`LOOKUPSWITCH` over a proven int-family load, whose arms stay in
  that suffix and reach `RETURN`; prefix-assigned extras read by those suffixes
  must have one compatible type on every bridge-taking path, and every retained
  path calls one hidden bridge with a trailing constant path id; and
- three or more direct this/super calls with the same identical straight-line
  suffix-copy proof, where every call additionally consumes the original
  receiver plus locally proven arguments (matching direct declared-argument
  loads; one `LNEG` over a direct declared long-argument load or its proven
  prefix extra-local copy; a tree of at
  most sixteen `LADD`, `LSUB`, `LMUL`, `LDIV`, `LREM`, `LAND`, `LOR`, `LXOR`,
  `LSHL`, `LSHR`, or `LUSHR` levels for a long argument, whose long leaves are
  declared long-argument loads, proven prefix extra-local copies of declared
  long-argument loads (including shift values and division/remainder operands),
  `LCONST_0`, `LCONST_1`, `LDC` of `Long`, or the admitted direct/proven-copy
  `LNEG`,
  and whose shift counts remain proven single-instruction int-family
  leaves (declared `ILOAD`, int-family constant, or proven prefix
  extra-local copy);
  a tree of at most sixteen `FADD`, `FSUB`, `FMUL`, `FDIV`, or `FREM` levels for
  a float argument, whose leaves are declared float-argument loads, a prefix
  extra-local `FLOAD` with one dominating `FSTORE` copy of a declared
  float-argument load, `FCONST_0`, `FCONST_1`, `FCONST_2`, `LDC` of `Float`,
  or one `FNEG` over a direct declared float-argument load or its proven
  prefix extra-local copy;
  a tree of at most sixteen `DADD`, `DSUB`, `DMUL`, `DDIV`, or `DREM` levels for
  a double argument, whose leaves are declared double-argument loads, a prefix
  extra-local `DLOAD` with one dominating `DSTORE` copy of a declared
  double-argument load, `DCONST_0`, `DCONST_1`, `LDC` of `Double`, or one
  `DNEG` over a direct declared double-argument load or its proven prefix
  extra-local copy;
  a prefix extra-local `ILOAD` with one dominating `ISTORE` copy of a declared
  int-family argument load; int-family constants; or one `INEG` over a direct
  declared int-family argument load or the same proven prefix extra-local copy,
  plus a tree of at most sixteen `IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`,
  `ISHL`, `ISHR`, `IUSHR`, `IDIV`, or `IREM` levels whose leaves are each one
  of those already-proven int-family inputs; one `IALOAD`, `BALOAD`,
  `CALOAD`, or `SALOAD` whose source is an unchanged declared array argument
  of the matching type (`[I`, `[B`/`[Z`, `[C`, or `[S`) or a prefix
  extra-local `ALOAD` with one dominating `ASTORE` copy of that same
  declared argument, and whose index is an int-family constant or one
  single-instruction declared or proven-prefix-copy `ILOAD`, including the
  composition where both the array source and index use those proven prefix
  copies; or one
  reference `AALOAD` whose source is an unchanged directly loaded declared
  array argument or its proven prefix extra-local copy, whose index is a
  proven int-family constant or one single-instruction declared or
  proven-prefix-copy `ILOAD` (including both copies together), and whose
  declared immediate component
  descriptor exactly matches the call argument descriptor); one isolated
  `ALOAD n; GETFIELD owner.name:desc` leaf where `n` is an unchanged declared
  object argument or one dominating prefix extra-local `ALOAD`/`ASTORE` copy
  of that argument, `owner` is exactly the declared source class, and `desc`
  has the same JVM invocation carrier as the call argument; one isolated
  `NEW owner; DUP; {0..6 args}; INVOKESPECIAL owner.<init>({0..6 X})V` leaf
  whose allocated reference descriptor exactly matches the call argument
  descriptor, where every `X` is int-family or long and every `arg` is one
  single-instruction proven leaf of the matching family; plus `LALOAD`,
  `FALOAD`, and `DALOAD` leaves from unchanged declared `[J`, `[F`, and `[D`
  arguments or one dominating prefix extra-local `ALOAD`/`ASTORE` copy, at
  int-family constant indexes or at one single-instruction declared or
  proven-prefix-copy `ILOAD` index, including when both the wide-array source
  and index use those proven prefix copies. The complete array-load tree stays
  in the retained bytecode prefix, so JVM null, bounds, widening, and
  reference-array behavior remains JVM-executed. The admitted field read,
  its receiver load, and the complete admitted allocation/initialization
  sequence also stay in the retained prefix, preserving JVM field-access and
  object-initialization behavior.

## Current rule

`split()` classifies prefix branches by target:

- A prefix jump or switch whose every target label is also in the prefix
  is **admitted**. Both edges remain in the retained bytecode, so a this/super
  call is still reachable.
- A prefix jump or switch whose target lands in the suffix is **rejected**
  (`Constructor prefix branches across the this/super call`) unless it matches
  one of the exact shared-suffix exceptions below.
- For a multi-call diamond, the one exception is the `GOTO` immediately after
  each non-final chain call. It must target the exact shared join label. The
  final chain call must fall through to that same label.
- One non-`GOTO` exception is admitted only for exactly two chain calls with no
  exception table. The first call must be followed by either one `ILOAD` of a
  declared int-family constructor argument and `IFEQ`, `IFNE`, `IFLT`, `IFGE`,
  `IFGT`, or `IFLE`, or two such direct `ILOAD`s and `IF_ICMPEQ`,
  `IF_ICMPNE`, `IF_ICMPLT`, `IF_ICMPGE`, `IF_ICMPGT`, or `IF_ICMPLE`. The jump
  must target the exact shared join and its fallthrough must be an immediate
  `RETURN`; the second call must fall through to the join. Receiver-stack
  analysis requires both calls to consume the original constructor receiver
  with no older stack values. The count-state CFG proof still requires exactly
  one call at the join and at the early return.
- One switch exception is admitted under the same two-call, empty-exception-
  table, empty-chain-entry-stack, and original-receiver restrictions. The first
  call must be followed by exactly one direct `ILOAD` of a declared int-family
  constructor argument and then `TABLESWITCH` or `LOOKUPSWITCH`. Every case and
  default must target the exact shared suffix label, an immediate prefix
  `RETURN`, or a prefix label whose first executable instruction is a direct
  `GOTO` to that suffix. The second call must fall through to the suffix. The
  count-state proof rejects zero-call suffix/return paths and any switch target
  that executes another chain call.
- One prefix-exit exception is admitted only for exactly two chain calls with
  no exception table and empty chain-entry stacks. The first call must be
  followed immediately by `RETURN`; the final call must fall through directly
  to the suffix. The suffix boundary is the instruction after that final call,
  so this rule does not depend on an otherwise unreferenced label surviving a
  class-file round trip.
- That prefix-exit form is conditional-extra-only: the normal extra-local
  analysis must prove one compatible type at every actual hidden-bridge entry,
  and at least one forwarded extra must be unassigned at the earlier,
  immediately returning chain call. The retained return never loads that local
  or invokes the bridge. No synthetic `null` or zero value is introduced.
- No additional prefix-to-suffix jump or switch shape is admitted. Before any
  selected chain call, such an edge can skip required initialization. After one
  selected call, the proven single-entry, single-hidden-bridge cases are already
  exactly the shared-join `GOTO`, conditional, and switch forms above. Mixed
  targets or intervening work outside those local proofs remain rejected before
  constructor, hidden-method, generated-source, or cache mutation.

Multiple direct this/super candidates are admitted only under a stricter
fail-closed rule:

- There must be at least two direct `<init>` calls targeting the constructor
  owner or its direct superclass.
- A count-state CFG analysis starts with zero chain calls, increments at every
  candidate, and rejects a path that reaches another candidate after one call,
  reaches the shared suffix with zero or multiple calls, or reaches a return
  without exactly one call. Every candidate must be reachable.
- Outside the immediate-prefix-return exception, the final candidate in
  bytecode order must be followed by a join label before the first suffix
  instruction. In the strict-diamond form, every earlier candidate's next
  executable instruction must be `GOTO` to that exact label. This proves that
  all suffix-taking prefix paths enter one identical range.
- The retained wrapper keeps the complete diamond, every chain call, their
  join label, and one hidden-bridge invocation. `createNativeBody` starts at
  the shared join and emits that suffix once.

One additional family is reduced to that same shared-join form:

- With two or more candidates, each must be immediately followed by a
  straight-line suffix copy ending in `RETURN`. The copies may be empty, in
  which case each call is immediately followed by `RETURN`.
- The copies may not contain branches, switches, throws, nested constructor
  calls, or unsafe constants. Non-executable labels and frames are ignored when
  comparing each normal copy through its first `RETURN`, while every executable
  instruction and operand in those normal copies must match the final
  bytecode-order copy.
- A try/catch entry is accepted when all three labels are before the first
  chain call, or when its protected range is wholly in the canonical final copy
  and its suffix-owned handler is one of the six proven isolated return or
  rethrow forms in a tail after the normal suffix return. A protected range
  wholly in that canonical copy may instead target one of the already-proven
  isolated prefix handlers. The noncanonical copies may not own any table
  labels, so normalization never leaves dangling entries.
- With three or more candidates, each call's complete input sequence must
  additionally start with direct `ALOAD 0`, or a direct `ALOAD` of the proven
  original-receiver alias after the prefix overwrite described below. In
  invocation order, each argument must then be either a direct load of a
  declared constructor argument with a matching JVM carrier; `ICONST_M1`
  through `ICONST_5`, `BIPUSH`, `SIPUSH`, or `LDC` of `Integer` for an
  int-family call argument; a prefix extra-local `ILOAD` with exactly one
  dominating overlapping write consisting of an `ISTORE` directly fed by a
  declared int-family argument `ILOAD`; or exactly one `INEG` over a direct
  `ILOAD` of a declared int-family constructor argument or the same proven
  prefix extra-local copy. An int-family
  argument may also have at most sixteen
  levels of `IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`,
  `IUSHR`, `IDIV`, or `IREM`. The operand proof recursively consumes one
  explicit depth budget per binary level, and the leaves remain direct loads,
  proven prefix copies, constants, or single-load `INEG` forms. Prefix
  extra-local int copies may be used by every admitted int binary, including
  `IDIV`, `IREM`, and shifts; unlike prefix long copies, they have no
  operator-specific exclusion. This admits `IDIV`/`IREM` as inner or outer
  nodes, including nesting with each other, while seventeen-or-more binary
  levels are rejected. `INEG` remains restricted to one declared or
  proven-copy `ILOAD`;
  a constant, double `INEG`, and a computed operand remain rejected.
  Shift-count masking is not reproduced by this proof: every admitted shift
  remains in the retained bytecode prefix and therefore keeps JVM shift
  semantics. Every admitted division or remainder also stays in that retained
  prefix, preserving JVM divide-by-zero and signed-overflow behavior.
- A call argument of any JVM carrier may instead be one isolated
  `ALOAD n; GETFIELD owner.name:desc` leaf. Local `n` must be a declared object
  constructor argument other than local 0 or an extra local with exactly one
  dominating prefix `ASTORE` directly fed by `ALOAD` of that argument. The
  declared source must remain unchanged, its class must exactly equal `owner`,
  and `desc` must have the same invocation carrier as the call parameter.
  Copies of local 0 or arrays, overwritten or computed receivers, mismatched
  owners or carriers, and `GETSTATIC` remain rejected. The receiver load and
  field read stay in the retained bytecode prefix, so JVM null checks and
  field-access semantics are unchanged.
- A reference call argument may instead be one isolated allocation leaf:
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V`, or
  `NEW owner; DUP; arg; INVOKESPECIAL owner.<init>(X)V`, or
  `NEW owner; DUP; arg1; arg2; INVOKESPECIAL owner.<init>(X1X2)V`, or
  `NEW owner; DUP; arg1; arg2; arg3;
  INVOKESPECIAL owner.<init>(X1X2X3)V`, or
  `NEW owner; DUP; arg1; arg2; arg3; arg4;
  INVOKESPECIAL owner.<init>(X1X2X3X4)V`, or
  `NEW owner; DUP; arg1; arg2; arg3; arg4; arg5;
  INVOKESPECIAL owner.<init>(X1X2X3X4X5)V`, or
  `NEW owner; DUP; arg1; arg2; arg3; arg4; arg5; arg6;
  INVOKESPECIAL owner.<init>(X1X2X3X4X5X6)V`. Every initializer parameter
  must be an int-family or long carrier, and each corresponding argument must
  be exactly one executable instruction accepted by the matching leaf proof:
  an int-family or long constant, a matching declared-argument load, or a
  proven prefix-copy load. The constructor owner must exactly match the
  allocated class and the allocated reference descriptor must exactly match
  the call parameter. The complete sequence stays in the retained JVM prefix.
  Missing `DUP`, seven or more initializer arguments, computed initializer
  inputs (including `INEG` and `LNEG`), float or double initializer
  arguments, unproven inputs, type mismatches, and every array-allocation
  opcode remain rejected.
- A long call argument may instead have at most sixteen levels of `LADD`, `LSUB`,
  `LMUL`, `LDIV`, `LREM`, `LAND`, `LOR`, or `LXOR`, recursively proving both
  long operands, or `LSHL`, `LSHR`, or `LUSHR`, recursively proving the long
  value while keeping the count as a non-recursive int-family leaf. A long
  leaf must be a matching declared-argument `LLOAD`, a prefix extra-local
  `LLOAD` with exactly one dominating overlapping write consisting of an
  `LSTORE` directly fed by a matching declared-argument `LLOAD`,
  `LCONST_0`, `LCONST_1`, or `LDC` of `Long`, one `LALOAD` from an unchanged
  declared `[J` argument or its proven prefix extra-local copy at an
  int-family constant index or one single-instruction declared or
  proven-prefix-copy `ILOAD` index, or one `LNEG` whose sole operand is a
  matching declared-argument `LLOAD` or the same proven prefix extra-local
  copy; an int-family count leaf uses the existing single-instruction int
  leaf proof: a declared-argument `ILOAD`, int-family constant, or proven
  prefix extra-local copy. A standalone `LNEG` leaf is admitted under the
  same declared-or-proven-copy restriction.
  Prefix extra-local long copies may be used as shift values and as either
  `LDIV`/`LREM` operand, while proven prefix extra-local int copies may be used
  as shift counts. This proof has its own explicit sixteen-level binary
  budget: seventeen-or-more nested long binaries, computed or unproven int shift
  counts, extra-local long shift counts, `LNEG` of a constant, double `LNEG`,
  and `LNEG` of an unproven extra-local or computed value remain rejected. The
  retained bytecode prefix executes admitted
  operations, preserving Java long wrapping, negate semantics, bitwise
  semantics, JVM divide-by-zero and signed-overflow behavior, and mask-63
  shift-count behavior without reproducing those semantics in C++.
- A float call argument may instead contain a tree of at most sixteen `FADD`,
  `FSUB`, `FMUL`, `FDIV`, or `FREM` levels. A float leaf must be a matching
  declared-argument `FLOAD`, a prefix extra-local `FLOAD` with exactly one
  dominating `FSTORE` directly fed by a matching declared-argument `FLOAD`,
  `FCONST_0`, `FCONST_1`, `FCONST_2`, or `LDC` of `Float`, one `FALOAD` from
  an unchanged declared `[F` argument or its proven prefix extra-local copy at
  an int-family constant index or one single-instruction declared or
  proven-prefix-copy `ILOAD` index, or one `FNEG` whose sole operand is a
  matching declared-argument `FLOAD` or its proven prefix extra-local copy.
  This proof has its own sixteen-level binary budget, which
  `FNEG` does not consume: seventeen-or-more nested float binaries, unproven or
  computed extra-local stores, `FNEG` of a constant, double `FNEG`, and `FNEG`
  of an unproven extra-local or computed value remain rejected, as do
  extra-local int and long operands and computed reference inputs.
  The admitted arithmetic stays in the retained bytecode prefix, preserving
  Java evaluation order, rounding, signed-zero, infinity, and NaN behavior
  without reproducing float arithmetic in C++.
- A double call argument may instead contain a tree of at most sixteen `DADD`,
  `DSUB`, `DMUL`, `DDIV`, or `DREM` levels. A double leaf must be a matching
  declared-argument `DLOAD`, a prefix extra-local `DLOAD` with exactly one
  dominating `DSTORE` directly fed by a matching declared-argument `DLOAD`,
  `DCONST_0`, `DCONST_1`, or `LDC` of `Double`, one `DALOAD` from an unchanged
  declared `[D` argument or its proven prefix extra-local copy at an
  int-family constant index or one single-instruction declared or
  proven-prefix-copy `ILOAD` index, or one `DNEG` whose sole operand is a
  matching declared-argument `DLOAD` or its proven prefix extra-local copy.
  This proof has its own sixteen-level binary budget, which `DNEG` does not
  consume: seventeen-or-more nested double binaries, unproven or computed
  extra-local stores, `DNEG` of a constant, double `DNEG`, and `DNEG` of an
  unproven extra-local or computed value remain rejected, as do extra-local
  int and long operands and computed reference inputs. The admitted arithmetic
  stays in the retained bytecode prefix, preserving JVM divide-by-zero, NaN,
  signed-zero, negate, and other double semantics without reproducing double
  arithmetic in C++.
- A receiver-state CFG analysis proves that each call consumes the original
  constructor receiver with no older operand-stack values. Every `ASTORE 0`
  must precede the first chain call. Besides an identity-preserving store, its
  direct value must be `ACONST_NULL` or `ALOAD` of a declared reference
  argument. A copied suffix may read an extra local only when a matching store
  precedes the first chain call and the local has one compatible type on every
  path reaching every chain call. Suffix-copy stores do not establish a
  forwarded value because noncanonical copies are discarded. The second
  strict-diamond split repeats the definite-assignment/type proof at the
  normalized join and packs each admitted extra.
- After all checks pass, every noncanonical suffix copy is replaced with
  `GOTO` and the final copy receives the shared join label. The existing
  strict-diamond split then retains every call and emits the canonical suffix
  once behind one hidden bridge. Canonical suffix tables and any admitted
  isolated prefix handlers follow the existing exception relocation pipeline:
  the independent IR body owns them and the wrapper omits them.

One bounded path-selected family uses the same single-bridge pipeline without
normalizing the suffixes to one copied join:

- Between two and eight reachable candidates are required, with an empty
  chain-entry stack. Each call must receive direct `ALOAD 0`, or a direct load
  of the saved original-receiver alias after the exact prefix overwrite
  described below, and the same locally proven argument-input families used by
  the bounded multi-return proof. Exception entries are admitted only when all
  three labels are before the first chain call, or when all three labels are
  wholly inside one proven nonempty suffix range. A protected range wholly
  inside one suffix may also target one of the already-proven isolated prefix
  handlers or one proven isolated handler tail immediately after the last
  suffix; the handler and optional isolated return block are relocated with
  that suffix table.
- Each call must fall through to its own nonempty suffix ending in `RETURN`.
  Any suffix may contain one unary `IFxx` over a direct declared int-family
  `ILOAD` or proven int-family extra, or one `IF_ICMPxx` whose second input is
  another such load or an int-family constant. Alternatively, it may contain
  one `TABLESWITCH` or `LOOKUPSWITCH` immediately preceded by a direct
  declared int-family `ILOAD` or proven int-family extra load. Every switch
  case/default target and every conditional arm must stay forward inside that
  suffix and reach `RETURN`; a `GOTO` is accepted only as part of one of these
  closed control-flow forms and only when it targets an in-suffix `RETURN`.
  A suffix cannot combine a conditional and a switch. Back edges, computed
  switch keys, throws, nested constructor calls, and unproven extra-local
  accesses are rejected. A suffix may load a prefix-assigned extra only when
  the extra has one exact compatible primitive/reference carrier on every path
  that reaches any hidden-bridge invocation. This includes bridge-taking paths
  whose selected suffix does not read that extra: all path-id call sites target
  the one fixed hidden-bridge descriptor and therefore must supply the same
  packed arguments. The suffix ranges may not overlap,
  and the final normal range must end at method end or immediately before the
  one isolated handler tail. The tail is not another path-id suffix. A path-id
  set may contain an identical pair when at least one suffix CFG differs. Each
  repeated range keeps its own path id; it is not merged into a copied join.
  Fully identical straight-line copies continue to use the existing one-join
  normalization above because that proof runs before path selection.
- The independent IR method appends one `int` parameter. It first branches on
  that path id, then contains every proven suffix instruction range. Two paths
  retain the existing `IFNE` dispatch. Three or more paths use a `TABLESWITCH`
  over the exact `0..n-1` range and an explicit `ACONST_NULL; ATHROW` default.
  A wholly-in-one-suffix try/catch entry is cloned with that range and owned by
  this independent body. A proven isolated prefix or method-end handler and its
  optional return block are appended to that body before its table is copied.
  Prefix-only entries stay on the bytecode wrapper.
  The source constructor retains its prefix and every chain call; each path
  loads the receiver, declared arguments, and proven extras in packed
  local-index order, then pushes its path id and invokes the same hidden bridge
  exactly once.
- The trailing selector occupies the first slot after all declared constructor
  parameters and packed extras. It therefore does not reuse a live extra slot,
  receiver slot, or category-2 parameter slot.

Unproven extra-local or aliased chain inputs, array loads from computed or
reassigned sources, computed, negated, or unproven extra-local indexes,
non-array or opcode-mismatched declared locals, prior array-store mutations,
unlisted primitive-result array loads, `GETFIELD` on local 0, an unproven or
overwritten extra local, a copy of local 0 or an array, a computed object, an
overwritten declared argument, an owner-incompatible declared argument, or
with a mismatched field carrier, `GETSTATIC`, non-isolated or unsupported
argument-taking `NEW`, `NEWARRAY`, `ANEWARRAY`, `MULTIANEWARRAY`, and other
unlisted reference computations, int-family binary expression
trees deeper than sixteen levels, long binary expression trees deeper than
sixteen levels, float or double expression trees deeper than the sixteen
admitted binary levels,
other unlisted long or double operations,
standalone non-int-family constants, `IINC`, other fields, method calls, stack
duplication, computed or rewritten receivers, or any other unlisted input
remain rejected.
Distinct joins, other conditional or switch forms,
unproven condition/switch-key loads, computed keys, escaping switch targets,
labels or control flow in a copied suffix, nonempty exception tables for the
two-call shared-join post-chain IF/switch forms, zero-call paths, unreachable
candidates, unproven or suffix-only extra-local accesses, and path-selected
per-call suffix sets above the bounded eight-call rule also remain rejected.
Multi-call tables remain rejected when their labels mix prefix and suffix,
span suffixes, cover a chain call, use an unsafe handler after the last suffix,
or use an unproven prefix handler. For identical-copy normalization, any suffix table
must be wholly in the canonical final copy; tables in discarded copies and
cross-copy ranges remain rejected. All-identical suffix sets that do not match
that normalizer, non-identity `ASTORE 0` outside the exact single-call,
strict-join, identical-copy, or path-id alias rules, and other unlisted catch
forms remain rejected. Constructor-prefix `JSR`/`RET` remains admitted only for
the already-proven straight-line subroutine with no exception table. A table
covering its call, body, `RET`, or generated inline clone, plus nested
subroutines and control flow that reaches extra work after a lexical `RET`,
remain rejected. This is intentionally a narrow set of proven constructor split
forms, not a general multi-exit constructor rewriter.

It also classifies writes to reference/array constructor-argument locals:

- A prefix `ASTORE` into a reference/array parameter slot is **admitted**. The
  retained prefix computes the replacement value and the wrapper loads that
  slot after the this/super call. Only the affected argument is widened to
  `java/lang/Object` in the hidden bridge and independent suffix descriptor, so
  a verifier-valid prefix may replace (for example) a declared `String`
  parameter with an `Object`.
- Unmodified argument descriptors remain exact; array descriptor information is
  not erased globally.
- An identity-preserving prefix `ASTORE 0` remains admitted when the existing
  must-style stack/local CFG analysis proves its input is the original
  constructor receiver. Copies through `ALOAD`/`ASTORE` locals and JVM stack
  shuffles preserve this identity; merges retain it only when every incoming
  path agrees.
- A single-call constructor may instead save the original receiver in a
  prefix alias, write another verifier-valid reference to local 0, and load the
  alias as the this/super call receiver. The same receiver analysis must prove
  that the selected call consumes the original receiver. An unreachable store,
  a failed frame analysis, or a call on overwritten local 0 is rejected before
  bridge or C++ mutation.
- The same alias-forwarding rule applies to a strict shared-join diamond with
  two or more calls when every earlier call has one direct `GOTO` to the join,
  the final call falls through to that join, and receiver-frame analysis proves
  that every call consumes the original receiver through the alias.
- Identical-copy normalization applies that strict-join rule after proving that
  every `ASTORE 0` is prefix-only and directly stores `ACONST_NULL` or a
  declared reference argument (apart from identity-preserving stores). For
  three or more copies, each selected call must directly load the receiver or
  its proven alias. Copied suffixes may read the alias local, or another
  prefix-assigned extra, only when the extra has one compatible type at every
  selected call and is definitely assigned at the normalized join.
  Normalization still emits one join and one hidden bridge.
- It also applies to bounded path-id distinct suffixes when every `ASTORE 0`
  occurs before the first chain call and directly stores `ACONST_NULL` or a
  declared reference argument, every call directly loads the proven original-
  receiver alias, and all normal distinct-suffix argument, stack, exception,
  and extra-local proofs pass. A suffix read of that alias is forwarded only
  when its packed extra is definitely assigned at every bridge-taking call.
  This does not extend the post-call IF/switch or immediate-prefix-return
  families.
- The wrapper deliberately continues to load local 0 as the first hidden-
  bridge argument. It does not substitute the alias: suffix `ALOAD 0` observes
  the overwritten Java local. If the suffix reads the alias, the existing
  extra-local proof forwards it separately; after the chain call that value is
  the initialized constructed receiver.
- Because overwritten local 0 may be `null` or an object from a bootstrap
  class, the alias-forwarding wrapper also passes the constructor owner's
  `Class` as shell-only metadata. Native class resolution uses that exact class
  loader without changing the IR meaning of local 0 or using the receiver alias
  for instance operations.
- Non-identity `ASTORE 0` remains rejected for every other multi-call
  shared-join form and every path-id or identical-copy shape outside the exact
  rules above.

Prefix stores into non-parameter locals are classified separately:

- `ALOAD`, `ILOAD`, `LLOAD`, `FLOAD`, `DLOAD`, and `IINC` in the suffix identify
  extra locals whose prefix values need to cross the split.
- A must-style prefix CFG analysis requires one compatible stored type on every
  path that reaches the hidden bridge. Normally that is also every path through
  the this/super call. The exact two-call immediate-prefix-return exception
  above may leave the local unassigned on its exiting call because that path
  cannot reach the suffix load or bridge. A missing bridge-path assignment or
  incompatible store family is rejected by `split()` before bridge or C++
  mutation, with the local index in the diagnostic.
- For path-selected suffixes, the same proof checks the incoming local state
  at every chain call that is followed by a bridge invocation. An assignment on
  only some bridge-taking paths is rejected, even when the selected suffix on
  an unassigned path does not read the local, because one native method and one
  descriptor serve every path and no synthetic argument is introduced. An
  immediate-return sibling that does not invoke the bridge remains outside this
  requirement.
- The independent suffix descriptor and hidden static bridge append the extra
  parameters in increasing local-index order. The wrapper loads them from their
  original local indexes after loading the receiver and declared constructor
  arguments.
- Only prefix-stored, suffix-read extras are appended. An unassigned local
  between two original indexes is not forwarded, so it does not need a
  descriptor entry or a value at the chain call.
- The independent suffix clone remaps forwarded loads, stores, and `IINC` from
  each original index to its packed trailing parameter slot. Unrelated
  suffix-only locals are moved after those parameters to keep local identities
  distinct. The retained prefix and wrapper are not remapped.
- Reference extras use `java/lang/Object`. Primitive extras retain the exact
  verifier carrier selected by their store/load family: `I`, `J`, `F`, or `D`.
  A packed `long` or `double` consumes two local slots. Category-2 overlap with
  another suffix read remains rejected.

Other guards remain unchanged:

- Suffix jumps/switches into the prefix are rejected.
- All other try/catch label placements crossing the split are rejected.
- Prefix `ASTORE 0` is rejected when the selected chain call is not proven to
  consume the original receiver. Outside the strict `GOTO` shared-join,
  identical-copy, and bounded path-id rules, multi-call shapes still require
  every store to be identity-preserving.
- `jsr`/`ret` remains unsupported.

A prefix branch into the suffix can bypass the mandatory chain call. A
cross-split exception region cannot preserve a bytecode handler edge from
exceptions raised by the native suffix. Those cases therefore remain unsafe
for this split shape.

Try/catch entries are classified independently and fail closed:

- If `start`, `end`, and `handler` all have instruction indexes below the
  suffix start, the entry is admitted. `postProcess` clones the complete entry
  with the retained prefix and does not expose it to the JNI shell or copy it
  into `createNativeBody`.
- Six exact mixed sequences are admitted when `start` and `end` are suffix
  labels and `handler` is either a prefix label or the start of one isolated
  tail immediately after the last suffix: `POP; RETURN`,
  `POP; GOTO ret`, `ASTORE n; RETURN`, `ASTORE n; GOTO ret`, `ATHROW`, and
  `ASTORE n; ALOAD n; ATHROW`, where `ret` stays in the same isolated prefix
  or method-end region and its first executable instruction is `RETURN`.
  Stack-map frames may separate the executable nodes. In every form the
  preceding executable instruction before a prefix `handler` must be `GOTO`.
  A method-end handler must follow a non-fallthrough instruction and be
  reachable only as an exception target. No jump or switch may target either
  kind of `handler`, and `handler` may not delimit a protected range.
- For either `GOTO ret` form, the handler's `GOTO` must be the only jump or
  switch target edge to `ret`; `ret` may not delimit a protected range, serve
  as an exception handler, or have a fallthrough predecessor.
- The `ASTORE` forms require a nonzero reference slot that is not a category-2
  hole. Return handlers require the next executable instruction to be the
  admitted `RETURN` or `GOTO`. Rethrow handlers require the same local to be
  loaded immediately before `ATHROW`. Extra work and other uses of the stored
  exception remain rejected. The handler local adds no hidden-bridge argument
  or synthetic value.
- Rethrow handlers are admitted only in the two straight-line forms above;
  prefix `GOTO`-to-rethrow variants remain rejected.
- `createNativeBody` appends the isolated handler and optional return block to
  the suffix clone and preserves the original exception-table edge.
  `postProcess` omits the dead prefix or method-end blocks from the bytecode
  wrapper. A method-end tail is never treated as another path-id suffix.
- If all three labels are in the suffix, the entry remains admitted and is
  cloned into `createNativeBody` for IR lowering.
- For the bounded two-to-eight-call path-selected form, an all-suffix entry is
  admitted only when start, end, and handler all belong to the same proven
  suffix range. It is remapped into the independent IR body as that range is
  appended. A start/end pair in one such suffix may instead target any of the
  same exact isolated prefix- or method-end-handler forms above. That handler
  and its optional isolated return block are cloned into the independent body,
  while the table and handler are omitted from the wrapper. Entries spanning
  two suffixes, covering a chain call, or using an unproven method-end handler
  fail before constructor mutation.
- For both the path-selected form and identical-copy normalization, an entry
  whose three labels all precede the first chain call stays entirely in the
  retained wrapper. Identical-copy normalization admits suffix entries only for
  the canonical copy because its earlier copies are removed.
- Relocatable suffix-range/prefix-handler entries are admitted for single-super,
  bounded two-to-eight path-id distinct-suffix constructors, and canonical
  identical-copy suffixes. The post-suffix method-end form is admitted only for
  path-id suffixes and the canonical identical-copy suffix.
- Every other mixed placement is rejected, including a prefix protected range
  with a suffix handler and any suffix protected range with a prefix handler
  that does not match one of the six proven isolated handler forms.
  The hidden bridge is outside every admitted prefix protected range, so native
  suffix exceptions cannot enter a bytecode handler before the bridge.

`createNativeBody` still emits initialized-this code only. For the bounded
path-selected rule it emits all suffixes behind synthetic path-id dispatch.
`postProcess` keeps the prefix plus every admitted this/super call in the source
constructor. Shared-join forms append one bridge invocation at the split;
the bounded path-selected form has one bytecode invocation site on each
exclusive path, all targeting the same hidden method. Prefix + this/super stay
in bytecode; no uninitialized-this prefix code is IR-lowered. Label cloning in
`cloneRange`/`createNativeBody` maps every label of the method, so cloned
prefix branches resolve correctly.

## Verification

Synthetic bytecode unit tests in
`obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`:

- A positive `Validated`-equivalent constructor (`Math.abs`, `IFNE`, throw, then
  `super`): `createNativeBody` succeeds and `IrMethodCompiler.processMethod`
  produces the native bridge while keeping the prefix branch and this/super call
  in the constructor.
- A prefix `Object`-to-`String` parameter-slot `ASTORE` is admitted with an
  `Object` bridge/suffix entry descriptor. Serializing and loading the rewritten
  class reaches the unresolved native bridge, proving JVM verification.
- An identity-preserving `ALOAD 0; DUP; ASTORE 0` prefix is retained with the
  this/super call while the field-writing suffix moves behind the hidden bridge.
  A serialized rewritten class verifies and reaches the unresolved bridge, and
  the full CMake/g++ transform has stdout parity under
  `java -Xverify:all -Xcheck:jni`.
- `admitsAndRewritesReceiverAliasForwardingBeforeSuper` loads and instantiates
  the original verifier-legal alias fixture, proves its rewritten wrapper keeps
  `ASTORE 0` and invokes super through `ALOAD 2`, and verifies that one hidden
  bridge still receives local 0 as its first body argument.
- `rejectsOverwrittenConstructorReceiverAtChainCallBeforeMutation` keeps the
  unsafe no-alias form fail-closed when the chain call consumes overwritten
  local 0, without allocating a hidden method.
- `receiverAliasForwardingCompilesAndRunsWithJavaParity` forwards the initialized
  receiver alias as an extra local while suffix `ALOAD 0` remains the declared
  argument. Non-null and null runs have matching stdout before and after the
  CMake/g++ transform under `java -Xverify:all -Xcheck:jni`.
- `forwardsPrefixExtraReferenceAndIntLocalsIntoSuffixDescriptors` checks linear
  reference and `int` extras in the independent suffix and hidden bridge
  descriptors, including identity local mappings for contiguous extras.
- `packsAndRemapsGappedPrefixExtrasInIndependentSuffix` checks that an unused
  hole is omitted, a higher reference extra is packed into the trailing
  parameter slot, suffix `ALOAD` and `IINC` indexes are remapped, and the
  wrapper still loads the original prefix index.
- `forwardsPrimitivePrefixExtrasWithExactTypesAndWideSlots` checks `I`, `J`, `F`,
  and `D` forwarding together, including the category-2 local positions.
- `rewrittenPrefixExtraLocalConstructorPassesJvmVerification` serializes and
  loads the rewritten owner and hidden class; invocation reaches the unresolved
  native bridge only after JVM verification succeeds.
- `rewrittenGappedPrefixExtraConstructorPassesJvmVerification` applies the same
  JVM verification check to the packed gapped-extra shape.
- `prefixExtraReferenceLocalCompilesAndRunsWithJavaParity` executes a synthetic
  class first as plain Java, then through the complete IR transform, generated
  CMake C++ library, hidden bridge registration, and
  `java -Xverify:all -Xcheck:jni`; both runs print
  `PREFIX-EXTRA-FORWARDED`.
- `gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity` repeats that
  compile-and-run parity path with the extra stored at local 3 and local 2
  unused.
- `admitsConditionallyAssignedExtraOnBridgePathOnly` proves the exact two-call
  prefix-exit form retains the unassigned immediate return while forwarding
  the bridge-only reference extra.
- `rewrittenConditionalBridgeExtraPassesJvmVerification` executes the prefix
  exit successfully and verifies that the assigned path reaches the unresolved
  native bridge.
- `conditionalBridgeExtraCompilesAndRunsWithJavaParity` exercises both paths
  through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`; both runs print `BRIDGE-ASSIGNED` and
  `PREFIX-EXIT`.
- `admitsMultipleSuperDiamondWithSharedSuffix` proves two retained direct
  superclass calls converge on one hidden bridge and that the independent
  native body contains only the shared suffix.
- `rewrittenMultipleSuperDiamondPassesJvmVerification` serializes and loads
  the rewritten subclass and its superclass; constructor invocation reaches
  the unresolved native bridge only after JVM verification succeeds.
- `multipleSuperDiamondCompilesAndRunsWithJavaParity` exercises positive and
  negative constructor arguments through the plain Java class and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`; both runs print the
  same superclass and shared-suffix field values.
- `admitsAndRewritesMultipleSuperDiamondWithReceiverAlias` proves both retained
  chain calls consume the saved receiver, local 0 remains the first bridge body
  argument, owner-`Class` metadata is present, and the rewritten shared-join
  wrapper verifies with one hidden bridge.
- `rejectsSharedJoinDiamondWhenOneCallUsesOverwrittenReceiverBeforeMutation`
  keeps the same diamond fail-closed when one call consumes overwritten local
  0, without allocating a hidden method.
- `receiverAliasMultipleSuperDiamondCompilesAndRunsWithJavaParity` exercises
  both join paths, a declared-reference overwrite and a null overwrite, plus a
  suffix read of the forwarded receiver alias through the complete CMake/g++
  JNI transform under `java -Xverify:all -Xcheck:jni`, requiring identical
  stdout.
- `admitsAndRewritesReceiverAliasPathIdDistinctSuffixes` proves two retained
  path-selected calls consume the saved receiver, local 0 remains the first
  bridge body argument, the suffix alias read is packed as an extra, owner-
  `Class` metadata precedes the path id, and both rewritten paths verify with
  one hidden bridge.
- `rejectsPathIdDistinctSuffixUsingOverwrittenReceiverBeforeMutation` keeps a
  two-suffix path-id shape fail-closed when one call consumes overwritten local
  0, without allocating a hidden method.
- `receiverAliasDistinctSuffixesCompileAndRunWithJavaParity` exercises both
  path ids and a null local-0 overwrite through the complete CMake/g++ JNI
  transform under `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsAndRewritesReceiverAliasIdenticalSuffixCopies` and
  `admitsAndRewritesReceiverAliasThreeIdenticalSuffixCopies` prove two- and
  three-copy normalization retains every alias-based chain call, replaces the
  earlier copies with one or two join `GOTO`s, and emits one owner-`Class`-
  aware hidden bridge for the canonical extra-free suffix.
- `admitsAndRewritesIdenticalSuffixCopiesWithPrefixExtra` proves a
  prefix-assigned `int` read by both identical copies is packed into the one
  canonical native suffix, while the retained calls converge through one
  normalization `GOTO` on one hidden bridge.
- `admitsAndRewritesReceiverAliasIdenticalSuffixCopiesReadingAlias` proves the
  saved receiver alias can also be packed and loaded by the canonical suffix;
  local 0 remains the wrapper's first bridge argument and owner-`Class`
  metadata remains present.
- `rejectsIdenticalSuffixCopyWithUnassignedExtraBeforeMutation` keeps an extra
  assigned on only one chain-call path fail-closed before normalization,
  hidden-method allocation, or constructor mutation.
- `identicalSuffixCopiesWithPrefixExtraCompileAndRunWithJavaParity` exercises
  both chain-call paths through the complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `rejectsIdenticalSuffixCopyUsingOverwrittenReceiverBeforeMutation` keeps the
  copied-suffix form fail-closed when one call consumes overwritten local 0,
  without allocating a hidden method or changing bytecode.
- `receiverAliasIdenticalSuffixCopiesCompileAndRunWithJavaParity` exercises all
  three normalized paths with declared-reference and null local-0 overwrites
  through the complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsPostChainConditionalBranchToSharedSuffix` proves the exact
  `ILOAD; IFNE sharedSuffix; RETURN` post-call shape emits only the shared
  suffix as the independent native body, preserves the conditional branch and
  early return in bytecode, and adds one hidden bridge without a synthetic
  join `GOTO`.
- `rewrittenPostChainConditionalBranchPassesJvmVerification` executes the
  early-return path and verifies both suffix-taking paths up to the unresolved
  native bridge after JVM verification.
- `postChainConditionalBranchCompilesAndRunsWithJavaParity` exercises the
  taken prefix-to-suffix edge, its fallthrough early return, and the alternate
  second-call path through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsPostChainIntCompareFamiliesToSharedSuffix` applies the same split proof
  to the five additional unary int-zero compares and all six binary
  `IF_ICMPxx` compares, each using only direct declared int-family argument
  loads.
- `rewrittenPostChainIntCompareFamiliesPassJvmVerification` verifies the
  suffix-taken, immediate-return, and alternate second-call paths for every
  newly admitted compare.
- `postChainIntCompareFamiliesCompileAndRunWithJavaParity` exercises those
  three paths for every newly admitted compare through plain Java and the
  complete CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring
  identical stdout.
- `admitsPostChainSwitchesToSharedSuffix` checks both switch opcodes with a
  direct shared-suffix target, a prefix `GOTO` target, and an immediate-return
  default while preserving both chain calls and one hidden bridge.
- `rewrittenPostChainSwitchesPassJvmVerification` verifies the direct and
  trampoline suffix cases, immediate-return default, and alternate second-call
  path for both switch opcodes.
- `postChainSwitchesCompileAndRunWithJavaParity` runs those cases through plain
  Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `rejectsPrefixBranchTargetingSuffixLabelBeforeMutation`,
  `rejectsSwitchPathThatSkipsEveryChainCallBeforeMutation`, and
  `rejectsMultipleSuperPathThatSkipsEveryChainCallBeforeMutation`, plus the
  `skip-super` variants in the immediate-return and distinct-suffix reject
  suites, keep direct and switch-based skip-initialization paths fail-closed.
  They preserve every constructor instruction object, generated source,
  hidden-method inventory, and the singular `MethodContext.proxyMethod`.
- `skipSuperConstructorShapesPassJava8JvmVerification` loads the untouched
  Java 8 direct-branch, table/lookup-switch, shared-join, immediate-return, and
  two-/three-distinct-suffix fixtures. Their ordinary paths construct normally,
  while their legal pre-super exceptional paths still throw without executing
  a chain call, documenting that IR rejection is deliberately conservative.
- `rejectsUnprovenPostChainSwitchShapes` applies the same pre-mutation checks to
  both switch opcodes with a computed key, extra prefix-return work, or an
  exception table. `unprovenPostChainSwitchShapesPassJava8JvmVerification`
  loads and executes the original Java 8 classes through their prefix-return,
  first-call suffix, and second-call suffix paths, documenting that these are
  conservative rejects of verifier-valid but unproven forms.
- `admitsMultipleSuperWithIdenticalLinearSuffixCopies` proves that two
  separate, identical field-writing suffix copies produce one native body and
  normalize to a retained two-call wrapper with one hidden bridge.
- `rewrittenIdenticalMultiSuperSuffixCopiesPassJvmVerification` executes both
  retained chain-call paths through JVM verification up to the unresolved
  native bridge.
- `identicalMultiSuperSuffixCopiesCompileAndRunWithJavaParity` exercises both
  paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsTwoSuperCallsWithDifferentStraightLineSuffixes` proves two distinct
  field-writing suffixes produce one path-selected independent IR body, two
  exclusive bytecode call sites for the same hidden bridge, and one
  `proxyMethod`.
- `rewrittenTwoDifferentSuffixesPassJvmVerification` serializes and loads the
  rewritten owner, superclass, and hidden class; both inputs reach the
  unresolved bridge only after JVM verification.
- `twoDifferentSuffixesCompileAndRunWithJavaParity` exercises both selector
  paths through plain Java and the complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`, requiring the same path-specific field
  values.
- `admitsTwoDistinctSuffixesWithInSuffixIntBranch` proves that each of two
  pairwise-distinct suffixes may keep one closed declared-argument `IFEQ`,
  while the rewritten constructor retains two chain calls and two invocations
  of one hidden bridge with a trailing path id.
- `rewrittenBranchedDistinctSuffixesPassJvmVerification` serializes and loads
  the rewritten owner, superclass, and hidden class, then selects both suffixes
  and both conditional arms up to the unresolved native bridge after JVM
  verification.
- `branchedDistinctSuffixesCompileAndRunWithJavaParity` exercises both path ids
  and both in-suffix branch arms through plain Java and the complete CMake/g++
  JNI transform under `java -Xverify:all -Xcheck:jni`, requiring identical
  stdout.
- `admitsTwoDistinctSuffixesWithInSuffixSwitch` checks both switch opcodes in
  pairwise-distinct suffixes, with direct declared int-family keys, closed
  in-suffix `RETURN` arms, two bytecode bridge sites, and one hidden method.
- `rewrittenSuffixSwitchDistinctSuffixesPassJvmVerification` selects both
  retained prefix paths and every table/lookup arm after loading the rewritten
  owner, superclass, and hidden class.
- `suffixSwitchDistinctSuffixesCompileAndRunWithJavaParity` exercises both
  switch opcodes, retained paths, and all switch arms through plain Java and the
  complete CMake/g++ JNI transform under `java -Xverify:all -Xcheck:jni`,
  requiring identical stdout.
- `rejectsEscapingOrUnprovenSuffixSwitchBeforeMutation` keeps cross-suffix and
  prefix targets plus computed keys fail-closed before hidden-method allocation.
- `admitsThreeDistinctSuffixesWithInSuffixIntBranch` proves that the same
  closed conditional proof works with three pairwise-distinct suffixes behind
  the existing path-id `TABLESWITCH` dispatch and one hidden bridge.
- `rewrittenThreeBranchedDistinctSuffixesPassJvmVerification` selects every
  prefix path with both condition values after loading the rewritten owner,
  superclass, and hidden class.
- `threeBranchedDistinctSuffixesCompileAndRunWithJavaParity` exercises those
  six argument combinations through plain Java and the complete CMake/g++ JNI
  transform under `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `rejectsThreeCallCrossSuffixBranchBeforeMutation` keeps branches into a
  sibling suffix or retained prefix fail-closed before bridge allocation.
- `rejectsCrossSuffixOrPrefixTargetingBranchBeforeMutation` checks that a
  suffix branch into its sibling suffix or back into retained pre-call
  bytecode fails before bridge allocation or constructor mutation. The
  pre-existing trivial-`GOTO` `branch` fixture remains rejected.
- `admitsTwoDistinctSuffixesWithProvenPrefixExtra` checks that both suffixes
  read the same definitely assigned extra, the independent descriptor places
  it before the trailing path-id `int`, and both wrapper sites call one hidden
  bridge.
- `rewrittenTwoDistinctSuffixesWithExtraPassJvmVerification` verifies both
  rewritten wrapper paths up to the unresolved bridge.
- `twoDistinctSuffixesWithExtraCompileAndRunWithJavaParity` writes
  extra-dependent values on both suffix paths and compares plain Java with the
  CMake/g++ JNI run under `java -Xverify:all -Xcheck:jni`.
- `rejectsUnassignedExtraOnDistinctSuffixBridgePathBeforeMutation` checks that
  assigning the extra on only one of two bridge-taking paths fails before a
  hidden method is allocated, even when the unassigned path's suffix never
  reads that local. The fixture remains verifier-valid Java 8 bytecode, as
  `unassignedExtraUnusedOnOneDistinctSuffixPassesJvmVerification` proves by
  loading and executing both original paths.
- `admitsThreeSuperCallsWithDistinctStraightLineSuffixes` proves three
  pairwise-distinct field-writing suffixes produce one `TABLESWITCH`-selected
  independent IR body, three exclusive calls to the same hidden bridge, and
  one `proxyMethod`.
- `rewrittenThreeDistinctSuffixesPassJvmVerification` serializes and loads the
  rewritten owner, superclass, and hidden class; all three inputs reach the
  unresolved bridge only after JVM verification.
- `threeDistinctSuffixesCompileAndRunWithJavaParity` exercises all three
  selector paths through plain Java and the complete CMake/g++ JNI transform
  under `java -Xverify:all -Xcheck:jni`, requiring the same field values.
- `admitsThreeSuperCallsWithPartlyIdenticalSuffixes` proves that
  `ICONST_4; POP; RETURN`, `ICONST_5; POP; RETURN`, and a repeated
  `ICONST_4; POP; RETURN` remain three separate ranges behind the existing
  path-id `TABLESWITCH`, with three calls to one hidden bridge.
- `rewrittenPartlyIdenticalSuffixesPassJvmVerification` loads the Java 8
  owner, superclass, and hidden class, then selects all three retained prefix
  paths up to the unresolved native bridge after JVM verification.
- `partlyIdenticalSuffixesCompileAndRunWithJavaParity` exercises positive,
  negative, and zero prefix paths with observable `4`, `5`, and `4` suffix
  results through plain Java and the complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`.
- `admitsAndRewritesIdenticalSuffixCopiesWithSuffixOnlyTryCatch` proves a table
  wholly in the canonical final copy is cloned into the one IR body and omitted
  from the normalized one-bridge wrapper.
- `admitsAndRewritesIdenticalSuffixCopiesWithMethodEndAthrowHandler` extends
  that canonical-copy proof to an exact method-end `ATHROW` handler.
- `admitsAndRewritesIdenticalSuffixCopiesWithRelocatedPrefixHandler` proves a
  canonical protected range may target the existing isolated `POP; RETURN`
  prefix-handler form, which is appended to the same IR body and removed from
  the wrapper.
- `rejectsIdenticalSuffixCopyTryCatchCrossingSplitBeforeMutation` keeps a table
  that covers chain calls and crosses copied ranges fail-closed before bytecode,
  hidden-method, or generated-source mutation.
- `identicalSuffixCopiesWithSuffixTryCatchCompileAndRunWithJavaParity`
  exercises both the wholly-canonical table and relocated-handler paths,
  including divide-by-zero catches, under
  `java -Xverify:all -Xcheck:jni`.
- `admitsFourSuperCallsWithPairwiseDistinctStraightLineSuffixes` proves the
  bounded dispatch and wrapper reconstruction also retain four chain calls
  and four invocations of one hidden bridge.
- `rejectsNinePathIdDistinctSuffixesBeforeMutation` fixes the path-id boundary
  at eight: nine reachable chain calls with pairwise-distinct nonempty suffixes
  remain fail-closed, preserving every instruction object, generated buffers,
  the singular `MethodContext.proxyMethod`, and the empty hidden-method pool.
- `ninePathIdDistinctSuffixesPassJvmVerification` loads the untouched Java 8
  fixture and executes all nine paths, proving that the bounded rejection is an
  IR admission limit rather than invalid constructor bytecode. The
  `MAX_DISTINCT_SUFFIXES = 8` cap remains unchanged.
- `admitsPrefixOnlyTryCatchOnThreeImmediateReturns`,
  `admitsPrefixOnlyTryCatchOnThreeDistinctSuffixes`, and
  `admitsPrefixOnlyTryCatchOnTwoDistinctSuffixes` prove that the complete
  exception entry stays on each rewritten wrapper while one hidden bridge
  remains responsible for the selected IR body.
- `admitsWhollyInOneSuffixTryCatchOnTwoDistinctSuffixes` proves that a table
  owned by one suffix is cloned into the path-id-dispatched IR body and omitted
  from the wrapper.
- `admitsAndRewritesPathIdSuffixTryCatchWithMethodEndAthrowHandler` proves that
  a one-suffix range may target an isolated `ATHROW` tail after the final
  suffix; the tail is cloned into the IR body and omitted from both wrappers.
- `rejectsUnsafeMethodEndHandlerBeforeMutation` keeps an extra-work tail
  fail-closed before hidden-method allocation or constructor mutation.
- `spanningAndChainCoveringTryCatchShapesPassJvmVerification` loads the
  untouched Java 8 cross-suffix class and the legacy-verifier chain-covering
  class, then executes both constructor paths for each shape. A classfile-52
  stack-map frame cannot encode the broken-uninitialized-this state produced
  by an exception edge from the constructor-chain invocation itself.
- `rejectsCrossSuffixAndChainCoveringMultiSuperTryCatchBeforeMutation` checks
  both a table whose labels span suffixes and a protected range covering a
  chain call. Rejection preserves instruction identity, the table and its
  labels, generated source, hidden methods, and the singular
  `MethodContext.proxyMethod`.
- `prefixOnlyMultiSuperTryCatchCompilesAndRunsWithJavaParity` runs normal and
  throwing prefix paths under `java -Xverify:all` before and after the complete
  CMake/g++ JNI transform.
- `suffixOnlyDistinctMultiSuperTryCatchCompilesAndRunsWithJavaParity` exercises
  normal and caught `IDIV` paths in one suffix plus the unchanged sibling
  suffix under `java -Xverify:all`, requiring identical stdout.
- `admitsRelocatedPrefixReturnHandlersOnTwoDistinctSuffixes` proves both
  `POP; RETURN` and safe `ASTORE n; RETURN` handlers move from the retained
  prefix into the path-id IR body with their one-suffix table, while two wrapper
  call sites continue to target one hidden bridge.
- `rejectsRelocatedPrefixHandlerSpanningDistinctSuffixesBeforeMutation` keeps
  the otherwise-relocatable handler fail-closed when its protected range spans
  two suffixes.
- `relocatedPrefixHandlerDistinctMultiSuperCompilesAndRunsWithJavaParity`
  exercises normal and caught `IDIV` paths plus the sibling path id through the
  complete CMake/g++ JNI transform under `java -Xverify:all`, requiring
  identical stdout.
- `pathIdMethodEndHandlerCompileAndRunWithJavaParity` exercises a method-end
  `POP; RETURN` handler on normal, caught `IDIV`, and sibling path-id cases
  through the complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `rejectsUnprovenThreeDistinctSuffixShapesBeforeMutation` and
  `rejectsUnprovenTwoDifferentSuffixShapesBeforeMutation` keep
  standalone-`GOTO`, skip-super, and unproven suffix-only extra-local variants
  fail-closed.
- `admitsMultipleSuperWithImmediateSeparateReturns` proves that an immediate
  `RETURN` after each of exactly two chain calls creates a `RETURN`-only native
  body and normalizes to one retained wrapper join and one hidden bridge.
- `rewrittenImmediateMultiSuperReturnsPassJvmVerification` executes both
  retained chain-call paths through JVM verification up to the unresolved
  native bridge.
- `immediateMultiSuperReturnsCompileAndRunWithJavaParity` executes both paths
  through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeSuperCallsWithImmediateSeparateReturns` proves that three calls
  using only the original receiver and direct declared-argument loads normalize
  to two retained `GOTO`s, one canonical `RETURN`, and one hidden bridge.
- `admitsThreeImmediateReturnsWithIaddOfProvenChainInputs` applies the same
  normalization to `ILOAD 1; ICONST_1; IADD`, one `INEG` over the declared
  load, and `ICONST_0`; it retains all three calls and one hidden bridge while
  the native body contains only the shared `RETURN`.
- `rewrittenThreeImmediateIaddSuperReturnsPassJvmVerification` selects each
  `IADD`, `INEG`, or constant call path and reaches the unresolved bridge only
  after the rewritten owner and hidden class pass JVM verification.
- `threeImmediateIaddSuperReturnsCompileAndRunWithJavaParity` selects all three
  paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithExtraLocalIntChainInputs`,
  `rewrittenThreeImmediateExtraLocalIntSuperReturnsPassJvmVerification`, and
  `threeImmediateExtraLocalIntSuperReturnsCompileAndRunWithJavaParity` admit a
  prefix `ISTORE` copy of a declared int-family `ILOAD` as a chain-input leaf.
  The copy and three `IADD`s stay in retained bytecode, the rewrite uses one
  hidden bridge, rewritten classes verify, and the complete CMake/g++ JNI
  transform matches plain Java. `admitsFormerExtraLocalIntChainInputLeftovers`
  covers the direct leaf, every admitted int binary, inner `IDIV`, and the
  four-level fixture; seventeen-or-more binary levels remain rejected.
- `admitsThreeImmediateReturnsWithExtraLocalIntInegChainInputs`, the matching
  rewritten-verification test, and
  `threeImmediateExtraLocalIntInegSuperReturnsCompileAndRunWithJavaParity`
  extend the single-`INEG` leaf to a proven prefix extra-local copy of a
  declared int-family `ILOAD`. The `ISTORE` copy and all three `INEG`s remain
  in retained JVM bytecode, one hidden bridge is used, `Integer.MIN_VALUE`
  keeps JVM negate semantics, and native stdout matches plain Java. `INEG` of
  a constant, double `INEG`, and computed operands remain fail-closed.
- `admitsThreeImmediateReturnsWithLaddOfProvenChainInputs` admits the former
  `long-binary` leftover: each of three retained chain calls computes its long
  argument with `LLOAD; LCONST_1; LADD`, while the native body contains only
  the shared `RETURN` behind one hidden bridge.
- `rewrittenThreeImmediateLaddSuperReturnsPassJvmVerification` selects every
  rewritten call path and reaches the unresolved hidden bridge only after the
  owner, long-taking superclass, and hidden class pass JVM verification.
- `threeImmediateLaddSuperReturnsCompileAndRunWithJavaParity` exercises all
  three paths, including a wrapping `Long.MAX_VALUE + 1`, through plain Java
  and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithExtraLocalLongChainInputs`,
  `rewrittenThreeImmediateExtraLocalLongSuperReturnsPassJvmVerification`, and
  `threeImmediateExtraLocalLongSuperReturnsCompileAndRunWithJavaParity` admit
  the former `long-extra-local` leftover: a prefix `LSTORE` copy of a declared
  `LLOAD` may be used as a long chain-input leaf. The copy and all three
  `LADD`s remain in the retained bytecode prefix, the rewrite uses one hidden
  bridge, rewritten classes verify, and the complete CMake/g++ JNI transform
  matches plain Java. Type-wrong extra-local long shift counts, unproven
  `LNEG` inputs, and seventeen-or-more nested long binaries remain rejected.
  This
  does not
  complete the production goal or authorize changing the default compiler
  path.
- `admitsThreeImmediateReturnsWithExtraLocalLongShiftValueAndDivRemChainInputs`
  and the matching rewritten-verification test admit a proven prefix
  extra-local long copy as the value of `LSHL` and as the dividend of `LDIV`
  or `LREM`. The two dedicated runtime tests exercise the shift-value and
  division/remainder families through the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring Java parity while keeping the
  long operations in the retained bytecode prefix behind one hidden bridge.
- `admitsThreeImmediateReturnsWithExtraLocalLongShiftCountChainInputs`,
  `rewrittenThreeImmediateExtraLocalLongShiftCountSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalLongShiftCountSuperReturnsCompileAndRunWithJavaParity`
  admit a proven prefix extra-local int copy as the count for `LSHL`, `LSHR`,
  and `LUSHR`. The copy and all shifts remain in retained JVM bytecode, all
  paths share one hidden bridge, rewritten classes verify, and the complete
  CMake/g++ JNI transform matches Java without reproducing mask-63 semantics
  in C++.
- `admitsThreeImmediateReturnsWithFaddOfProvenChainInputs` admits leaf-only
  `FLOAD; FCONST_1; FADD` chain arguments while retaining all three additions,
  all three chain calls, two join `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateFaddSuperReturnsPassJvmVerification` selects every
  rewritten path through a Java 8 owner and float-taking superclass, reaching
  the unresolved hidden bridge only after JVM verification succeeds.
- `threeImmediateFaddSuperReturnsCompileAndRunWithJavaParity` exercises finite
  float inputs through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring exactly matching float stdout.
- `admitsThreeImmediateReturnsWithExtraLocalFloatChainInputs`,
  `rewrittenThreeImmediateExtraLocalFloatSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalFloatSuperReturnsCompileAndRunWithJavaParity`
  admit the former `float-extra-local` leftover: a prefix `FSTORE` copy of a
  declared `FLOAD` may be used as a float chain-input leaf. The copy and all
  three `FADD`s remain in the retained bytecode prefix, the rewrite uses one
  hidden bridge, rewritten classes verify, and the complete CMake/g++ JNI
  transform matches plain Java.
- `admitsThreeImmediateReturnsWithExtraLocalFloatFnegChainInputs`,
  `rewrittenThreeImmediateExtraLocalFloatFnegSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalFloatFnegSuperReturnsCompileAndRunWithJavaParity`
  admit the former `float-fneg-extra-local` leftover: one `FNEG` may consume a
  proven prefix extra-local float copy at each chain call. The copy and all
  three negations stay in the retained bytecode prefix, one hidden bridge is
  shared, rewritten classes verify, and the complete CMake/g++ JNI transform
  matches plain Java.
- `rejectsUnprovenFloatComputedChainInputsBeforeMutation` keeps float binaries
  at seventeen-or-more levels and unsafe `FNEG` forms (constant, double negate,
  computed value) fail-closed without constructor or hidden-method mutation.
  Cross-carrier extra-local int and long operands remain rejected by the float
  proof. This does not authorize changing the default compiler path.
- `admitsThreeImmediateReturnsWithDaddOfProvenChainInputs` admits leaf-only
  `DLOAD; DCONST_1; DADD` chain arguments while retaining all three additions,
  all three chain calls, two join `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateDaddSuperReturnsPassJvmVerification` selects every
  rewritten path through a Java 8 owner and double-taking superclass, reaching
  the unresolved hidden bridge only after JVM verification succeeds.
- `threeImmediateDaddSuperReturnsCompileAndRunWithJavaParity` exercises finite
  double inputs through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring exactly matching double stdout.
- `admitsThreeImmediateReturnsWithDsubAndDmulOfProvenChainInputs` checks
  leaf-only double subtraction and multiplication while retaining all three
  matching operations, all three chain calls, two join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateDsubDmulSuperReturnsPassJvmVerification` selects all
  three paths for both admitted opcodes and reaches each unresolved hidden
  bridge only after the rewritten Java 8 classes pass JVM verification.
- `threeImmediateDsubDmulSuperReturnsCompileAndRunWithJavaParity` exercises
  both double shapes through plain Java and the complete CMake/g++ JNI
  transform under `-Xverify:all -Xcheck:jni`, requiring exactly matching
  finite-value stdout.
- `admitsThreeImmediateReturnsWithDdivAndDremOfProvenChainInputs` checks
  leaf-only double division and remainder while retaining all three matching
  operations, all three chain calls, two join `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateDdivDremSuperReturnsPassJvmVerification` selects all
  three paths for both admitted opcodes and reaches each unresolved hidden
  bridge only after the rewritten Java 8 classes pass JVM verification.
- `threeImmediateDdivDremSuperReturnsCompileAndRunWithJavaParity` exercises
  both double shapes through plain Java and the complete CMake/g++ JNI
  transform under `-Xverify:all -Xcheck:jni`, requiring exactly matching
  stdout.
- `admitsThreeImmediateReturnsWithDnegOfProvenChainInputs`,
  `rewrittenThreeImmediateDnegSuperReturnsPassJvmVerification`, and
  `threeImmediateDnegSuperReturnsCompileAndRunWithJavaParity` prove that one
  `DNEG` over a declared double `DLOAD` remains in the retained prefix,
  rewrites to one hidden bridge, passes JVM verification, and matches plain
  Java through the complete CMake/g++ JNI transform.
- `admitsThreeImmediateReturnsWithExtraLocalDoubleChainInputs`,
  `rewrittenThreeImmediateExtraLocalDoubleSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalDoubleSuperReturnsCompileAndRunWithJavaParity`
  admit the former `double-extra-local` leftover: a prefix `DSTORE` copy of a
  declared `DLOAD` may be used as a double chain-input leaf. The copy and all
  three `DADD`s remain in the retained bytecode prefix, the rewrite uses one
  hidden bridge, rewritten classes verify, and the complete CMake/g++ JNI
  transform matches plain Java.
- `admitsThreeImmediateReturnsWithExtraLocalDoubleDnegChainInputs`,
  `rewrittenThreeImmediateExtraLocalDoubleDnegSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalDoubleDnegSuperReturnsCompileAndRunWithJavaParity`
  admit one `DNEG` whose sole operand is that proven prefix extra-local
  `DLOAD`. The prefix copy and all three negations remain in retained
  bytecode, the rewrite uses one hidden bridge, rewritten classes verify, and
  the complete CMake/g++ JNI transform matches plain Java.
- `rejectsUnprovenDoubleComputedChainInputsBeforeMutation` keeps double
  binaries at seventeen-or-more levels, computed extra-local stores, and unsafe
  `DNEG` forms (constant, double negate, and computed operands) fail-closed
  without constructor or hidden-method mutation.
  Cross-carrier extra-local int and long operands and computed reference inputs
  remain outside the double proof. This does not authorize changing the default
  compiler path.
- `admitsThreeImmediateReturnsWithFnegOfProvenChainInputs` admits one `FNEG`
  over each direct declared float load while retaining all three negations,
  all three chain calls, two join `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateFnegSuperReturnsPassJvmVerification` selects every
  rewritten path through a Java 8 owner and float-taking superclass, reaching
  the unresolved hidden bridge only after JVM verification succeeds.
- `threeImmediateFnegSuperReturnsCompileAndRunWithJavaParity` exercises finite
  positive and negative float inputs through plain Java and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring exactly
  matching float stdout.
- `admitsThreeImmediateReturnsWithFsubAndFmulOfProvenChainInputs` checks
  leaf-only float subtraction and multiplication while retaining all three
  matching operations, all three chain calls, two join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateFsubFmulSuperReturnsPassJvmVerification` selects all
  three paths for both admitted opcodes and reaches each unresolved hidden
  bridge only after the rewritten Java 8 classes pass JVM verification.
- `threeImmediateFsubFmulSuperReturnsCompileAndRunWithJavaParity` exercises
  both float shapes through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring exactly matching finite-value
  stdout.
- `admitsThreeImmediateReturnsWithFdivAndFremOfProvenChainInputs` checks
  leaf-only float division and remainder while retaining all three matching
  operations, all three chain calls, two join `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateFdivFremSuperReturnsPassJvmVerification` selects all
  three paths for both admitted opcodes and reaches each unresolved hidden
  bridge only after the rewritten Java 8 classes pass JVM verification.
- `threeImmediateFdivFremSuperReturnsCompileAndRunWithJavaParity` exercises
  both float shapes with nonzero constant divisors through plain Java and the
  complete CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring
  exactly matching finite-value stdout.
- `admitsThreeImmediateReturnsWithLsubAndLmulOfProvenChainInputs` checks the
  former `long-lsub` and `long-lmul` leftovers with only declared long loads
  and `LCONST_1` as operands, retaining all arithmetic and chain calls behind
  one hidden bridge.
- `rewrittenThreeImmediateLsubLmulSuperReturnsPassJvmVerification` selects all
  three paths for both admitted opcodes and reaches each unresolved hidden
  bridge only after the rewritten classes pass JVM verification.
- `threeImmediateLsubLmulSuperReturnsCompileAndRunWithJavaParity` exercises
  subtraction, multiplication, and wrapping subtraction through plain Java
  and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithBitwiseProvenLongChainInputs` checks
  leaf-only `LAND`, `LOR`, and `LXOR` over a declared long load and
  `LCONST_1`, retaining all operations and chain calls behind one hidden
  bridge.
- `rewrittenThreeImmediateLongBitwiseSuperReturnsPassJvmVerification` selects
  every rewritten bitwise call path and reaches the unresolved hidden bridge
  only after the rewritten classes pass JVM verification.
- `threeImmediateLongBitwiseSuperReturnsCompileAndRunWithJavaParity` exercises
  all three long bitwise families through plain Java and the complete CMake/g++
  JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithShiftProvenLongChainInputs` checks leaf-only
  `LSHL`, `LSHR`, and `LUSHR` over a declared long load and an int-family
  constant leaf, retaining all shift operations and chain calls behind one
  hidden bridge.
- `rewrittenThreeImmediateLongShiftSuperReturnsPassJvmVerification` selects
  every rewritten long-shift path and reaches the unresolved hidden bridge only
  after the rewritten classes pass JVM verification.
- `threeImmediateLongShiftSuperReturnsCompileAndRunWithJavaParity` exercises
  signed and unsigned long shifts through plain Java and the complete CMake/g++
  JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithLdivAndLremOfProvenChainInputs` checks
  leaf-only `LDIV` and `LREM` over a declared long load and `LCONST_1`,
  retaining all operations and chain calls behind one hidden bridge.
- `rewrittenThreeImmediateLdivLremSuperReturnsPassJvmVerification` selects
  every rewritten long division and remainder path and reaches the unresolved
  hidden bridge only after the rewritten classes pass JVM verification.
- `threeImmediateLdivLremSuperReturnsCompileAndRunWithJavaParity` exercises
  both long operations through plain Java and the complete CMake/g++ JNI
  transform under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithLnegOfProvenChainInputs`,
  `rewrittenThreeImmediateLnegSuperReturnsPassJvmVerification`, and
  `threeImmediateLnegSuperReturnsCompileAndRunWithJavaParity` prove that one
  `LNEG` over a declared long `LLOAD` remains in the retained prefix, rewrites
  to one hidden bridge, passes JVM verification, and matches plain Java through
  the complete CMake/g++ JNI transform.
- `admitsThreeImmediateReturnsWithExtraLocalLongLnegChainInputs`, the matching
  rewritten-verification test, and
  `threeImmediateExtraLocalLongLnegSuperReturnsCompileAndRunWithJavaParity`
  extend that same single-`LNEG` leaf to a proven prefix extra-local copy of a
  declared long `LLOAD`. The `LSTORE` copy and every `LNEG` stay in retained
  bytecode, one hidden bridge is used, `Long.MIN_VALUE` retains JVM negate
  semantics, and native stdout matches plain Java.
- `admitsTwoLevelNestedLongChainInputs`,
  `rewrittenTwoLevelNestedLongChainInputsPassJvmVerification`, and
  `twoLevelNestedLongChainInputsCompileAndRunWithJavaParity` cover the former
  `long-nested-ladd` leftover, `LDIV` as either an inner or outer node, and an
  outer long shift whose count remains an int-family leaf. The retained prefix
  executes both long binary levels, all paths share one hidden bridge, the
  rewritten classes verify, and the complete CMake/g++ JNI transform matches
  plain Java.
- `admitsFourLevelNestedLongChainInputs`,
  `rewrittenFourLevelNestedLongChainInputsPassJvmVerification`, and
  `fourLevelNestedLongChainInputsCompileAndRunWithJavaParity` cover bounded
  four-level `LADD`, inner and outer `LDIV`, and an outer `LSHL` whose count
  remains an int-family leaf. The retained prefix executes every operation,
  all paths share one hidden bridge, rewritten Java 8 classes verify, and the
  complete CMake/g++ JNI transform matches plain Java.
- `admitsFiveLevelNestedLongChainInputs`,
  `rewrittenFiveLevelNestedLongChainInputsPassJvmVerification`, and
  `fiveLevelNestedLongChainInputsCompileAndRunWithJavaParity` cover the former
  five-level `LADD` and inner-`LDIV` leftovers under the sixteen-level
  budget. The retained prefix executes every operation, one hidden bridge is
  shared, rewritten classes verify, and native stdout matches plain Java.
- `admitsThreeImmediateReturnsWithDeclaredIndexAaloadChainInputs` and
  `admitsThreeImmediateReturnsWithExtraLocalIndexAaloadChainInputs` admit a
  reference `AALOAD` index only when it is one direct declared `ILOAD` or a
  single `ILOAD` of a proven prefix extra-local copy. The matching rewritten
  verification and CMake/g++ Java-parity coverage keep the copy and all three
  `AALOAD`s in the JVM prefix behind one hidden bridge.
- `rejectsUnprovenReferenceComputedIndexesBeforeMutation` keeps binary
  computed, `INEG`, computed-store extra-local, and unproven extra-local
  indexes fail-closed without constructor or hidden-method mutation.
- `rejectsUnprovenLongComputedChainInputsBeforeMutation` keeps
  seventeen-or-more
  long binary levels, computed or unproven extra-local int shift counts,
  type-wrong extra-local long shift counts, `LNEG` of a constant, double
  `LNEG`, and computed `LNEG` operands fail-closed without constructor or
  hidden-method mutation. Unproven reference computations stay rejected;
  the proven declared-array or extra-local-copy `AALOAD` with a constant or
  single-instruction `ILOAD` index is admitted separately.
- `admitsThreeImmediateReturnsWithExtraLocalIntArrayIaloadChainInputs`,
  `rewrittenThreeImmediateExtraLocalIntArrayIaloadSuperReturnsPassJvmVerification`,
  and
  `threeImmediateExtraLocalIntArrayIaloadSuperReturnsCompileAndRunWithJavaParity`
  cover constant-index `IALOAD` leaves whose `int[]` source is one dominating
  prefix extra-local copy of an unchanged declared argument. The copy and each
  `IALOAD` stay in retained JVM bytecode, one hidden bridge is shared, rewritten
  Java 8 classes verify, and native stdout matches plain Java. Computed stores,
  overwritten copies or source arguments, prior array stores, non-`int[]`
  sources, and unproven indexes remain rejected before mutation.
- `admitsThreeImmediateReturnsWithIntFamilyArrayLoadChainInputs`,
  `rewrittenThreeImmediateIntFamilyArrayLoadsPassJvmVerification`, and
  `threeImmediateIntFamilyArrayLoadsCompileAndRunWithJavaParity` cover
  constant-index `BALOAD`, `CALOAD`, and `SALOAD` leaves from unchanged
  declared `[B`/`[Z`, `[C`, and `[S` arguments. Each load stays in
  retained JVM bytecode so sign/zero extension, null, and bounds stay
  JVM-executed, one hidden bridge is shared, rewritten Java 8 classes
  verify, and native stdout matches plain Java. Extra-local array sources and
  opcode/type mismatches remain rejected before mutation.
- `admitsThreeImmediateReturnsWithIntArrayLoadIndexInputs`,
  `rewrittenThreeImmediateIntArrayLoadIndexesPassJvmVerification`, and
  `threeImmediateIntArrayLoadIndexesCompileAndRunWithJavaParity` admit an
  `IALOAD`, `BALOAD`, `CALOAD`, or `SALOAD` index only when it is one direct
  declared `ILOAD` or one `ILOAD` of a proven prefix extra-local copy. The
  combined runtime JAR covers both index forms for all four opcodes while
  preserving JVM null, bounds, and primitive widening behavior. Binary or
  negated indexes and computed or overwritten extra-local copies remain
  rejected before constructor or hidden-method mutation.
- `admitsThreeImmediateReturnsWithExtraArrayExtraIndexChainInputs`,
  `rewrittenThreeImmediateExtraArrayExtraIndexPassJvmVerification`, and
  `threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity` prove the
  composition of a dominating prefix `ALOAD`/`ASTORE` array copy with a
  dominating prefix `ILOAD`/`ISTORE` index copy for `AALOAD`, `IALOAD`,
  `BALOAD`, `CALOAD`, and `SALOAD`. Both copies and every array load remain in
  retained JVM bytecode behind one hidden bridge; the runtime JAR covers
  `AALOAD` and `IALOAD` under `-Xverify:all -Xcheck:jni`.
  `rejectsUnprovenExtraArrayExtraIndexChainInputsBeforeMutation` keeps
  computed or overwritten copies, prior array stores, wrong sources, and
  computed or negated indexes fail-closed before mutation. Wide array-load
  extra sources/indexes are covered separately below.
- `rejectsUnprovenExtraLocalArrayAaloadSourcesBeforeMutation` and
  `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification` keep
  computed-store array sources, overwritten extra-local or declared sources,
  prior array stores, and primitive-array results fail-closed without
  constructor or hidden-method mutation. The verification audit loads the
  untouched classfile-52 owners and executes selectors `7`, `-7`, and `0`,
  preserving each selected value or the overwritten-copy fixture's intended
  null-array failure. An `AALOAD` from `[[I` validly produces an `[I`
  reference, but remains conservative and unadmitted. The admitted
  extra-array plus extra-index composition is unchanged.
- `admitsThreeImmediateReturnsWithWideArrayLoadChainInputs`,
  `rewrittenThreeImmediateWideArrayLoadsPassJvmVerification`, and
  `threeImmediateWidePrimitiveArrayLoadsCompileAndRunWithJavaParity` cover
  constant-index `LALOAD`, `FALOAD`, and `DALOAD` leaves from unchanged direct
  declared `[J`, `[F`, and `[D` arguments, including leaves under the existing
  sixteen-level binary budget. The complete loads stay in retained JVM
  bytecode so null, bounds, and category-two behavior remain JVM-executed.
  `rejectsUnprovenWideArrayLoadChainInputsBeforeMutation` keeps computed or
  `INEG` indexes, computed-store or overwritten extra-local indexes,
  opcode/type mismatches, overwritten arrays, prior array stores, and
  seventeen-level trees rejected before constructor or hidden-method
  mutation.
- `admitsThreeImmediateReturnsWithWideArrayLoadIndexInputs`,
  `rewrittenThreeImmediateWideArrayLoadIndexesPassJvmVerification`, and
  `threeImmediateWideArrayLoadIndexesCompileAndRunWithJavaParity` admit an
  `LALOAD`, `FALOAD`, or `DALOAD` index only when it is one direct declared
  `ILOAD` or one `ILOAD` of a proven prefix extra-local copy. The combined
  runtime JAR covers both index forms for all three opcodes while preserving
  JVM null, bounds, and category-two behavior. Binary or negated indexes and
  computed or overwritten extra-local copies remain rejected before
  constructor or hidden-method mutation.
- `admitsThreeImmediateReturnsWithExtraLocalWideArrayLoadChainInputs`,
  `rewrittenThreeImmediateExtraLocalWideArrayLoadsPassJvmVerification`, and
  `threeImmediateExtraLocalWidePrimitiveArrayLoadsCompileAndRunWithJavaParity`
  cover constant-index `LALOAD`, `FALOAD`, and `DALOAD` leaves whose `[J`,
  `[F`, or `[D` source is one dominating prefix extra-local copy of an
  unchanged declared argument. The copy and each load stay in retained JVM
  bytecode, one hidden bridge is shared, rewritten Java 8 classes verify, and
  native stdout matches plain Java.
- `admitsThreeImmediateReturnsWithWideExtraArrayExtraIndexChainInputs`,
  `rewrittenThreeImmediateWideExtraArrayExtraIndexPassJvmVerification`, and
  `threeImmediateWideExtraArrayExtraIndexCompileAndRunWithJavaParity` cover
  the composition where both the matching wide-array source and its
  single-instruction index are dominating prefix extra-local copies. The exact
  retained shape is `ALOAD 3; ASTORE 4; ILOAD 2; ISTORE 5`, followed at each
  chain call by `ALOAD 4; ILOAD 5; LALOAD|FALOAD|DALOAD` for an
  `(II[J|[F|[D)V` constructor. Both copies and every array load stay in
  retained JVM bytecode behind one hidden bridge.
  `rejectsUnprovenWideExtraArrayExtraIndexChainInputsBeforeMutation` keeps
  computed stores or indexes, overwritten copies or source arguments, prior
  array stores, negated indexes, and mismatched sources rejected before
  constructor or hidden-method mutation.
- `admitsTwoLevelNestedFloatChainInputs`,
  `rewrittenTwoLevelNestedFloatChainInputsPassJvmVerification`, and
  `twoLevelNestedFloatChainInputsCompileAndRunWithJavaParity` cover bounded
  two-level float trees with outer and inner `FDIV` positions and a two-sided
  `FADD` tree. The retained prefix executes every float operation, all paths
  share one hidden bridge, rewritten Java 8 classes verify, and the complete
  CMake/g++ JNI transform matches plain Java.
- `admitsTwoLevelNestedDoubleChainInputs`,
  `rewrittenTwoLevelNestedDoubleChainInputsPassJvmVerification`, and
  `twoLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` cover bounded
  two-level double trees with outer and inner `DDIV` positions and a two-sided
  `DADD` tree. The retained prefix executes every double operation, all paths
  share one hidden bridge, rewritten Java 8 classes verify, and the complete
  CMake/g++ JNI transform matches plain Java.
- `admitsThreeLevelNestedDoubleChainInputs`,
  `rewrittenThreeLevelNestedDoubleChainInputsPassJvmVerification`, and
  `threeLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` cover bounded
  three-level `DADD` plus inner and outer `DDIV` positions. The retained prefix
  executes every double operation, all paths share one hidden bridge, rewritten
  Java 8 classes verify, and the complete CMake/g++ JNI transform matches plain
  Java.
- `admitsFourLevelNestedDoubleChainInputs`,
  `rewrittenFourLevelNestedDoubleChainInputsPassJvmVerification`, and
  `fourLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` cover bounded
  four-level `DADD` plus inner and outer `DDIV` positions. The retained prefix
  executes every double operation, all paths share one hidden bridge, rewritten
  Java 8 classes verify, and the complete CMake/g++ JNI transform matches plain
  Java.
- `admitsFiveLevelNestedDoubleChainInputs`,
  `rewrittenFiveLevelNestedDoubleChainInputsPassJvmVerification`, and
  `fiveLevelNestedDoubleChainInputsCompileAndRunWithJavaParity` cover
  five-level `DADD` plus inner and outer `DDIV` positions. The retained prefix
  executes every operation, one hidden bridge is shared, rewritten classes
  verify, and native stdout matches plain Java.
- `admitsThreeLevelNestedFloatChainInputs`,
  `rewrittenThreeLevelNestedFloatChainInputsPassJvmVerification`, and
  `threeLevelNestedFloatChainInputsCompileAndRunWithJavaParity` cover bounded
  three-level `FADD` plus inner and outer `FDIV` positions. The retained prefix
  executes every float operation, all paths share one hidden bridge, rewritten
  Java 8 classes verify, and the complete CMake/g++ JNI transform matches plain
  Java.
- `admitsFourLevelNestedFloatChainInputs`,
  `rewrittenFourLevelNestedFloatChainInputsPassJvmVerification`, and
  `fourLevelNestedFloatChainInputsCompileAndRunWithJavaParity` cover bounded
  four-level `FADD` plus inner and outer `FDIV` positions. The retained prefix
  executes every float operation, all paths share one hidden bridge, rewritten
  Java 8 classes verify, and the complete CMake/g++ JNI transform matches plain
  Java.
- `admitsFiveLevelNestedFloatChainInputs`,
  `rewrittenFiveLevelNestedFloatChainInputsPassJvmVerification`, and
  `fiveLevelNestedFloatChainInputsCompileAndRunWithJavaParity` cover
  five-level `FADD` and inner `FDIV`. The retained prefix executes every
  operation, one hidden bridge is shared, rewritten classes verify, and native
  stdout matches plain Java.
- `rejectsUnprovenFloatComputedChainInputsBeforeMutation` keeps
  seventeen-or-more
  nested float binaries and unsafe constant, double-negate, and computed-value
  `FNEG` forms fail-closed without constructor or hidden-method mutation.
- `admitsThreeImmediateReturnsWithIdivAndIremOfProvenChainInputs` checks
  leaf-only `ILOAD; ICONST_2; IDIV` and `ILOAD; ICONST_2; IREM` chain
  arguments, retains both operations with all three calls and two join
  `GOTO`s, and creates one hidden bridge whose independent body builds.
- `rewrittenThreeImmediateIdivIremSuperReturnsPassJvmVerification` selects the
  positive, negative, and zero prefix paths of a Java 8 constructor and reaches
  the unresolved bridge only after the rewritten owner and hidden class pass
  JVM verification.
- `threeImmediateIdivIremSuperReturnsCompileAndRunWithJavaParity` exercises
  all three paths with nonzero constant divisors through plain Java and the
  complete CMake/g++ JNI transform under `java -Xverify:all -Xcheck:jni`,
  requiring identical stdout.
- `admitsNestedIdivIremWithinThreeLevelChainInputs` checks `IDIV`/`IREM` as
  inner nodes of non-trapping arithmetic, as outer nodes over proven binary
  operands, nested with division, and in a three-level mixed tree. All
  arithmetic remains in the retained prefix behind one hidden bridge.
- `rewrittenNestedIdivIremChainInputsPassJvmVerification` loads every admitted
  nested shape and reaches its unresolved hidden bridge only after JVM
  verification succeeds.
- `nestedIdivIremChainInputsCompileAndRunWithJavaParity` executes all admitted
  nesting directions and the three-level mixed tree through plain Java and the
  complete CMake/g++ JNI transform under `java -Xverify:all -Xcheck:jni`,
  requiring identical stdout.
- `admitsFourLevelNestedIntFamilyChainInputs` checks the bounded fourth level
  with the former `four-level-iadd` and inner-`IDIV` leftovers plus an
  outer-`IDIV` mixed tree. All arithmetic remains in the retained prefix and
  the rewrite still allocates one hidden bridge.
- `rewrittenFourLevelNestedChainInputsPassJvmVerification` loads every admitted
  four-level shape and reaches the unresolved hidden bridge only after JVM
  verification succeeds.
- `fourLevelNestedChainInputsCompileAndRunWithJavaParity` executes all three
  admitted four-level shapes through plain Java and the complete CMake/g++ JNI
  transform under `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsFiveLevelNestedIntFamilyChainInputs`,
  `rewrittenFiveLevelNestedChainInputsPassJvmVerification`, and
  `fiveLevelNestedChainInputsCompileAndRunWithJavaParity` cover the former
  five-level `IADD` and inner-`IDIV` leftovers. The retained prefix executes
  every operation, one hidden bridge is shared, rewritten classes verify, and
  native stdout matches plain Java.
- Dedicated admit and rewritten-verification tests cover the former nine-level
  `IADD`, `LADD`, `FADD`, and `DADD` leftovers. The retained JVM prefix
  executes all arithmetic behind one hidden bridge, and the combined
  `nineLevelNestedChainInputsCompileAndRunWithJavaParity` CMake/g++ JNI runtime
  matches plain Java for all four carriers under `-Xverify:all -Xcheck:jni`.
  Seventeen-level fixtures remain rejected before constructor or hidden-method
  mutation, proving that the sixteen-level family budgets stay bounded and
  fail closed.
- `seventeenLevelNestedBinariesPassJava8JvmVerification` loads the untouched
  classfile-52 `IADD`, `LADD`, `FADD`, and `DADD` fixtures and executes their
  ordinary positive, negative, and zero construction paths. This confirms
  that the bounded rejection is conservative for verifier-valid bytecode; it
  does not admit seventeen-level trees or change the default compiler path.
- `admitsThreeImmediateReturnsWithIsubAndImulOfProvenChainInputs` applies the
  same leaf-only proof to one `ISUB` path, one `IMUL` path, and one direct-load
  path; it retains both arithmetic opcodes, all three calls, two shared-join
  `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateIsubImulSuperReturnsPassJvmVerification` serializes
  and loads the rewritten owner and hidden class, then selects all three paths
  up to the unresolved bridge after JVM verification.
- `threeImmediateIsubImulSuperReturnsCompileAndRunWithJavaParity` selects the
  subtraction, multiplication, and direct-load paths through plain Java and the
  complete CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring
  identical stdout.
- `admitsThreeImmediateReturnsWithBitwiseProvenChainInputs` applies the same
  leaf-only proof to `IAND`, `IOR`, and `IXOR` paths; it retains all three
  bitwise opcodes and chain calls, two shared-join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateBitwiseSuperReturnsPassJvmVerification` serializes
  and loads the rewritten owner and hidden class, then selects all three
  bitwise paths up to the unresolved bridge after JVM verification.
- `threeImmediateBitwiseSuperReturnsCompileAndRunWithJavaParity` selects the
  `IAND`, `IOR`, and `IXOR` paths through plain Java and the complete CMake/g++
  JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithShiftProvenChainInputs` applies the same
  leaf-only proof to `ISHL`, `ISHR`, and `IUSHR` paths; it retains all three
  shift opcodes and chain calls, two shared-join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateShiftSuperReturnsPassJvmVerification` serializes and
  loads the rewritten owner and hidden class, then selects all three shift
  paths up to the unresolved bridge after JVM verification.
- `threeImmediateShiftSuperReturnsCompileAndRunWithJavaParity` selects the
  `ISHL`, `ISHR`, and `IUSHR` paths through plain Java and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical
  stdout while the retained shift bytecode keeps JVM count masking.
- `admitsThreeImmediateReturnsWithNestedProvenChainInputs` checks two-level
  trees for every admitted non-trapping int binary family, retains their
  arithmetic in the bytecode prefix, and keeps the existing three-call,
  one-join hidden-bridge shape.
- `rewrittenThreeImmediateNestedInputsPassJvmVerification` selects the
  positive, negative, and zero paths of a Java 8 constructor with nested
  `IADD` input and reaches the unresolved bridge only after JVM verification.
- `threeImmediateNestedInputsCompileAndRunWithJavaParity` exercises those
  three paths through plain Java and the complete CMake/g++ JNI transform
  under `java -Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithThreeLevelProvenChainInputs` checks three
  immediate superclass returns whose call inputs are bounded three-level
  `IADD` trees over declared int-family loads and constants. The rewrite keeps
  all input arithmetic in bytecode, normalizes to one join, and allocates one
  hidden native bridge.
- `rewrittenThreeImmediateThreeLevelSuperReturnsPassJvmVerification` selects
  every rewritten call path and reaches the unresolved bridge only after the
  owner and hidden class pass JVM verification.
- `threeImmediateThreeLevelSuperReturnsCompileAndRunWithJavaParity` exercises
  left-, right-, and mixed-nested three-level inputs through plain Java and the
  complete CMake/g++ JNI transform under `java -Xverify:all -Xcheck:jni`,
  requiring identical stdout.
- `admitsThreeSuperCallsWithIdenticalNonemptyLinearSuffixCopies` proves that
  three instruction-identical `ICONST_4; POP; RETURN` copies normalize to two
  retained `GOTO`s, one canonical suffix, and one hidden bridge while retaining
  the same `IADD`/`INEG`/constant chain-input proof.
- `rewrittenThreeIdenticalNonemptySuffixCopiesPassJvmVerification` serializes
  the rewritten owner, superclass, and hidden class, then selects all three
  paths up to the unresolved native bridge after JVM verification.
- `threeIdenticalNonemptySuffixCopiesCompileAndRunWithJavaParity` selects all
  three paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsPrefixOnlyTryCatchAndRetainsItInRewrittenConstructor` proves that the
  independent suffix has no prefix handler while the rewritten constructor
  retains the complete prefix exception-table entry and forwards its assigned
  local to the suffix bridge.
- `admitsSuffixOnlyTryCatchInNativeBody` proves that an all-suffix entry is
  still cloned and lowered.
- `rewrittenPrefixOnlyTryCatchConstructorPassesJvmVerification` loads the
  rewritten owner, superclass, and hidden bridge class, then executes the catch
  path through JVM verification.
- `prefixOnlyTryCatchConstructorCompilesAndRunsWithJavaParity` compares normal
  and caught inputs through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`.
- `admitsSuffixTryCatchWithIsolatedPrefixReturnHandler` checks that the
  previously admitted mixed handler is present in the independent suffix CFG
  and absent from the bytecode wrapper.
- `rewrittenRelocatedPrefixReturnHandlerPassesJvmVerification` loads the
  rewritten owner and reaches the unresolved bridge after JVM verification.
- `relocatedPrefixReturnHandlerCompilesAndRunsWithJavaParity` compares normal
  division and caught divide-by-zero paths through plain Java and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`.
- `admitsSuffixTryCatchWithIsolatedPrefixGotoReturnHandler` checks that
  `POP; GOTO ret; RETURN` and its exception edge are present in the independent
  suffix CFG while both isolated prefix blocks are absent from the wrapper.
- `rejectsUnsafePrefixGotoReturnHandlersBeforeMutation` covers extra handler
  work, a non-`RETURN` target, and an extra incoming edge to `ret`.
- `rewrittenRelocatedPrefixGotoReturnHandlerPassesJvmVerification` serializes
  and loads the rewritten owner plus hidden class and reaches the unresolved
  bridge on the caught-path input after JVM verification.
- `relocatedPrefixGotoReturnHandlerCompilesAndRunsWithJavaParity` compares
  normal division and caught divide-by-zero paths through plain Java and the
  complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`.
- `admitsSuffixTryCatchWithIsolatedPrefixAstoreReturnHandler` and
  `admitsSuffixTryCatchWithIsolatedPrefixAstoreGotoReturnHandler` check that
  `ASTORE n` consumes the caught exception in the independent suffix CFG while
  the isolated prefix blocks are absent from the wrapper.
- `rewrittenRelocatedPrefixAstoreReturnHandlerPassesJvmVerification` serializes
  and loads the rewritten owner plus hidden class and reaches the unresolved
  bridge on the caught-path input after JVM verification.
- `relocatedPrefixAstoreReturnHandlerCompilesAndRunsWithJavaParity` compares
  normal division and caught divide-by-zero paths through plain Java and the
  complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`.
- `rejectsUnsafePrefixAstoreReturnHandlersBeforeMutation` covers receiver slot
  zero, a category-2 hole, extra handler work, a stored-exception use, a
  non-`RETURN` target, and an extra incoming edge to `ret`.
- `admitsSuffixTryCatchWithIsolatedPrefixAthrowHandler` and
  `admitsSuffixTryCatchWithIsolatedPrefixAstoreAthrowHandler` check that the
  exact straight-line rethrow blocks and exception edges move to the
  independent suffix while disappearing from the bytecode wrapper.
- `rewrittenRelocatedPrefixAthrowHandlerPassesJvmVerification` loads the
  rewritten owner plus hidden class and reaches the unresolved bridge on the
  caught-path input after JVM verification.
- `relocatedPrefixAthrowHandlerCompilesAndRunsWithJavaParity` compares normal
  division and rethrown divide-by-zero paths through plain Java and the complete
  CMake/g++ JNI transform under `java -Xverify:all -Xcheck:jni`.
- `rejectsUnsafePrefixAthrowHandlersBeforeMutation` covers receiver slot zero,
  `POP; ATHROW`, extra handler work, a missing reload, and another use of the
  stored exception.
- `rejectsEveryMixedTryCatchLabelPlacement` checks all six mixed prefix/suffix
  assignments at non-boundary labels and verifies rejection before mutation.
- `rejectsUnprovenGetfieldChainInputsBeforeMutation` keeps direct or
  extra-local uses of local 0, overwritten source or extra-local holders,
  computed holders, mismatched field carriers, and `GETSTATIC` rejected. Every
  constructor instruction object, generated buffer, hidden-method inventory,
  and the singular `MethodContext.proxyMethod` remain unchanged.
- `unprovenGetfieldChainInputShapesPassJava8JvmVerification` loads the
  untouched classfile-52 overwritten-holder, computed-holder, and `GETSTATIC`
  fixtures and constructs all three ordinary paths. These remain conservative
  rejects rather than admissions. Direct and extra-local reads from
  uninitialized `this`, plus the field-carrier mismatch, remain rejected but
  are inherently not verifier-valid.
- `admitsThreeImmediateReturnsWithNewArgChainInputs`,
  `rewrittenThreeImmediateNewArgChainInputsPassJvmVerification`, and
  `threeImmediateNewArgChainInputsCompileAndRunWithJavaParity` admit only the
  isolated `NEW owner; DUP; INVOKESPECIAL owner.<init>()V` reference leaf. All
  three copies remain in retained JVM bytecode, the rewrite uses one hidden
  bridge, rewritten classes verify, and the CMake/g++ JNI transform matches
  plain Java.
- `admitsThreeImmediateReturnsWithNewOneArgChainInputs`,
  `rewrittenThreeImmediateNewOneArgChainInputsPassJvmVerification`, and
  `threeImmediateNewOneArgChainInputsCompileAndRunWithJavaParity` extend that
  leaf to the isolated
  `NEW StringBuilder; DUP; ICONST_1; INVOKESPECIAL StringBuilder.<init>(I)V`
  form. All four instructions stay in each retained JVM prefix, the native
  body contains neither that `NEW` nor its `INVOKESPECIAL`, the rewrite keeps
  one hidden bridge and singular `MethodContext.proxyMethod`, and selectors
  `7`, `-7`, and `0` preserve plain-Java/CMake/g++ JNI parity under
  `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewLongArgChainInputs`,
  `rewrittenThreeImmediateNewLongArgChainInputsPassJvmVerification`, and
  `threeImmediateNewLongArgChainInputsCompileAndRunWithJavaParity` admit the
  isolated
  `NEW Date; DUP; LCONST_1; INVOKESPECIAL Date.<init>(J)V` form. The allocation,
  duplicate, long constant, and initializer call stay in each retained JVM
  prefix; the native body contains neither that `NEW` nor its `INVOKESPECIAL`.
  The rewrite keeps one hidden bridge and singular `MethodContext.proxyMethod`,
  while classfile-52 selectors `7`, `-7`, and `0` preserve verification and
  plain-Java/CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewTwoArgChainInputs`,
  `rewrittenThreeImmediateNewTwoArgChainInputsPassJvmVerification`, and
  `threeImmediateNewTwoArgChainInputsCompileAndRunWithJavaParity` extend the
  isolated leaf to
  `NEW Point; DUP; ICONST_1; ICONST_2; INVOKESPECIAL Point.<init>(II)V`.
  All five instructions stay in each retained JVM prefix, the native body
  contains neither that `NEW` nor its `INVOKESPECIAL`, and the rewrite keeps
  one hidden bridge and singular `MethodContext.proxyMethod`. Rewritten
  classfile-52 bytecode and all three selector paths preserve JVM verification
  and plain-Java/CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewExtraLocalTwoArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalTwoArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalTwoArgChainInputsCompileAndRunWithJavaParity`
  compose that two-argument leaf with a proven prefix extra-local int copy:
  `NEW Point; DUP; ILOAD 3; ICONST_2;
  INVOKESPECIAL Point.<init>(II)V`. The prefix `ILOAD 2; ISTORE 3`, all three
  allocations, and their initializer calls stay in retained JVM bytecode;
  the native body contains only `RETURN`, and the rewrite keeps one hidden
  bridge and singular `MethodContext.proxyMethod`. Rewritten classfile-52
  bytecode and selector paths `7`, `-7`, and `0` preserve JVM verification
  and plain-Java/CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewExtraLocalThreeArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalThreeArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalThreeArgChainInputsCompileAndRunWithJavaParity`
  compose the three-argument leaf with the same proven prefix extra-local int
  copy: `NEW Color; DUP; ILOAD 3; ICONST_2; ICONST_3;
  INVOKESPECIAL Color.<init>(III)V`. The prefix `ILOAD 2; ISTORE 3`, complete
  allocations, and initializer calls stay in retained JVM bytecode; the
  native body contains only `RETURN`, and the rewrite keeps one hidden bridge
  and singular `MethodContext.proxyMethod`. The existing per-argument
  int-family proof admits this composition without a processor change.
  Rewritten classfile-52 bytecode and selector paths `7`, `-7`, and `0`
  preserve JVM verification and plain-Java/CMake/g++ JNI parity under
  `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewExtraLocalFourArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalFourArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalFourArgChainInputsCompileAndRunWithJavaParity`
  compose the four-argument leaf with the same proven prefix extra-local int
  copy: `NEW Insets; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4;
  INVOKESPECIAL Insets.<init>(IIII)V`. The prefix `ILOAD 2; ISTORE 3`,
  complete allocations, and initializer calls stay in retained JVM bytecode;
  the native body contains only `RETURN`, and the rewrite keeps one hidden
  bridge and singular `MethodContext.proxyMethod`. The existing per-argument
  int-family proof admits this composition without a processor change.
  Rewritten classfile-52 bytecode and selector paths `7`, `-7`, and `0`
  preserve JVM verification and plain-Java/CMake/g++ JNI parity under
  `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewExtraLocalFiveArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalFiveArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalFiveArgChainInputsCompileAndRunWithJavaParity`
  compose the five-argument leaf with the same proven prefix extra-local int
  copy: `NEW GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4;
  ICONST_5; INVOKESPECIAL GregorianCalendar.<init>(IIIII)V`. The prefix
  `ILOAD 2; ISTORE 3`, complete allocations, and initializer calls stay in
  retained JVM bytecode; the native body contains only `RETURN`, and the
  rewrite keeps one hidden bridge and singular `MethodContext.proxyMethod`.
  The existing per-argument int-family proof admits this composition without
  a processor change. Rewritten classfile-52 bytecode and selector paths `7`,
  `-7`, and `0` preserve JVM verification and plain-Java/CMake/g++ JNI parity
  under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewThreeArgChainInputs`,
  `rewrittenThreeImmediateNewThreeArgChainInputsPassJvmVerification`, and
  `threeImmediateNewThreeArgChainInputsCompileAndRunWithJavaParity` extend the
  isolated leaf to
  `NEW Color; DUP; ICONST_1; ICONST_2; ICONST_3;
  INVOKESPECIAL Color.<init>(III)V`. All six instructions stay in each
  retained JVM prefix, the native body contains neither that `NEW` nor its
  `INVOKESPECIAL`, and the rewrite keeps one hidden bridge and singular
  `MethodContext.proxyMethod`. Rewritten classfile-52 bytecode and all three
  selector paths preserve JVM verification and plain-Java/CMake/g++ JNI parity
  under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewFourArgChainInputs`,
  `rewrittenThreeImmediateNewFourArgChainInputsPassJvmVerification`, and
  `threeImmediateNewFourArgChainInputsCompileAndRunWithJavaParity` extend the
  isolated leaf to
  `NEW Insets; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4;
  INVOKESPECIAL Insets.<init>(IIII)V`. All seven instructions stay in each
  retained JVM prefix, the native body contains neither that `NEW` nor its
  `INVOKESPECIAL`, and the rewrite keeps one hidden bridge and singular
  `MethodContext.proxyMethod`. Rewritten classfile-52 bytecode and selector
  paths `7`, `-7`, and `0` preserve JVM verification and
  plain-Java/CMake/g++ JNI parity while retaining all four `Insets` field
  values under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewFiveArgChainInputs`,
  `rewrittenThreeImmediateNewFiveArgChainInputsPassJvmVerification`, and
  `threeImmediateNewFiveArgChainInputsCompileAndRunWithJavaParity` extend the
  isolated leaf to
  `NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4;
  ICONST_5; INVOKESPECIAL GregorianCalendar.<init>(IIIII)V`. All eight
  instructions stay in each retained JVM prefix, the native body contains
  neither that `NEW` nor its `INVOKESPECIAL`, and the rewrite keeps one hidden
  bridge and singular `MethodContext.proxyMethod`. Rewritten classfile-52
  bytecode and selector paths `7`, `-7`, and `0` preserve JVM verification
  and plain-Java/CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewSixArgChainInputs`,
  `rewrittenThreeImmediateNewSixArgChainInputsPassJvmVerification`, and
  `threeImmediateNewSixArgChainInputsCompileAndRunWithJavaParity` extend the
  isolated leaf to
  `NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4;
  ICONST_5; BIPUSH 6;
  INVOKESPECIAL GregorianCalendar.<init>(IIIIII)V`. All nine instructions
  stay in each retained JVM prefix, the native body contains neither that
  `NEW` nor its `INVOKESPECIAL`, and the rewrite keeps one hidden bridge and
  singular `MethodContext.proxyMethod`. Rewritten classfile-52 bytecode and
  selector paths `7`, `-7`, and `0` preserve JVM verification and
  plain-Java/CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`.
- `admitsThreeImmediateReturnsWithNewExtraLocalSixFifthSixthArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalSixFifthSixthArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalSixFifthSixthArgChainInputsCompileAndRunWithJavaParity`
  compose that six-argument leaf with the same proven prefix extra-local int
  copy as the fifth and sixth initializer arguments:
  `NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4;
  ILOAD 3; ILOAD 3;
  INVOKESPECIAL GregorianCalendar.<init>(IIIIII)V`. The prefix
  `ILOAD 2; ISTORE 3`, complete allocations, and initializer calls stay in
  retained JVM bytecode; the native body contains only `RETURN`, and the
  rewrite keeps one hidden bridge and singular `MethodContext.proxyMethod`.
  The existing per-argument int-family proof admits this composition without
  a processor change. Rewritten classfile-52 bytecode and selector paths `7`,
  `-7`, and `0` preserve JVM verification and plain-Java/CMake/g++ JNI parity
  under `-Xverify:all -Xcheck:jni`.
- `rejectsUnprovenNewChainInputsBeforeMutation` covers an uninitialized
  allocation, missing `DUP`, computed and `GETSTATIC` initializer inputs, a
  seven-int-argument synthetic `SevenIntHolder` initializer, `NEWARRAY`,
  `ANEWARRAY`, `MULTIANEWARRAY`,
  and a descriptor-mismatched allocation while preserving every constructor
  instruction object, generated buffer, hidden-method inventory, and the
  singular `MethodContext.proxyMethod`.
- `unprovenNewChainInputShapesPassJava8JvmVerification` loads the untouched
  classfile-52 computed-argument, `GETSTATIC`-argument,
  seven-int-argument synthetic `SevenIntHolder`, exact-type-conservative
  mismatch, and all three array-allocation fixtures, then constructs all
  three ordinary paths. These remain conservative rejects rather than
  admissions. The raw uninitialized-reference and missing-`DUP` fixtures
  remain rejected but are inherently not verifier-valid.
- `rejectsUnprovenWideNewChainInputsBeforeMutation` keeps isolated `NEW`
  initializer arguments with float or double carriers fail-closed.
  Every constructor instruction object, generated buffer, hidden-method
  inventory, and the singular `MethodContext.proxyMethod` remain unchanged.
- `unprovenWideNewChainInputShapesPassJava8JvmVerification` loads untouched
  classfile-52 `Point2D.Float(FF)` and `Point2D.Double(DD)` fixtures and
  constructs all three ordinary paths. These are verifier-valid conservative
  rejects, not admissions.
- `unprovenPostCallAndAstoreZeroShapesPassJava8JvmVerification` loads the
  untouched classfile-52 constructor with `ICONST_0; POP` after its first
  chain call and constructs selectors `7`, `-7`, and `0`. This extra post-call
  work remains a conservative reject. The `ACONST_NULL; ASTORE 0` fixture also
  remains rejected before mutation, but cannot participate in a successful
  JVM load because it overwrites uninitialized `this`; this is distinct from
  the admitted identity-preserving `ALOAD 0; DUP; ASTORE 0` form.
- Three-call negatives reject direct extra-local inputs, every admitted
  arithmetic, bitwise, or shift binary opcode using an extra local, a
  seventeen-level nested binary input,
  extra-local `IDIV`/`IREM` operands, an inner `IDIV` tree containing an
  extra-local or static-invoke operand, a rewritten `ASTORE 0` receiver,
  post-call extra work, a standalone-`GOTO` suffix, a zero-call return, and
  skip-super paths before mutation. Other multi-call negatives still cover
  mixed or spanning try/catch tables, a table covering a chain call, and a
  path that executes two chain calls. Existing prefix-to-suffix,
  suffix-to-prefix, and conditionally assigned extra-local negatives remain.
- Existing unsupported-opcode fallback still restores the original constructor.
- `rejectsConstructorJsrRetWithExceptionTableBeforeMutation` keeps constructor
  `JSR`/`RET` exception ranges, a nested subroutine, and a branch reaching work
  after a lexical `RET` fail-closed. The original version-50 instruction
  objects and exception entries, generated buffers, hidden-method inventory,
  and singular `MethodContext.proxyMethod` remain unchanged.
- `unprovenConstructorJsrRetShapesPassJava8JvmVerification` serializes and
  executes every loadable untouched reject fixture without the IR transform.
  Each version-50 constructor completes its legacy subroutine and stores the
  expected field value. The branch that jumps across a lexical `RET` is
  inherently verifier-invalid and remains reject-before-mutation-only. The
  admitted straight-line, no-exception-table constructor and its one hidden
  bridge are unchanged.

The focused gate was executed with:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

This increment adds three extra-local five-argument `NEW` composition tests
on top of the landed extra-local four-argument and isolated five-argument
coverage. Child-local JUnit XML reports 522 `IrCompilerTest` tests and 7
`CodegenModeTest` tests (529 total), all passing. These are not parent totals;
the parent re-runs the focused gate after integration.

This focused suite includes the existing constructor branch/parameter-store,
constant-dynamic, invokedynamic, and monitor harnesses.

This document records the split admission rule only. It does not assert any JDK
end-to-end support level.
