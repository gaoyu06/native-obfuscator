# IR phase 17 status

Phase 17 extends the optional direct Java bytecode → typed CFG IR → C++/JNI
compiler with the JVM wide stack-shuffle family: `DUP2`, `DUP_X2`,
`DUP2_X1`, `DUP2_X2`, and `POP2`. These bytecodes only reorder or remove
existing SSA values; they create no IR instruction and make no JNI call.
The CLI and API default remains `legacy`, unsupported methods retain
per-method legacy fallback, and `sources/cppsnippets.properties` remains
present. This phase does not change the evaluator or reader.

Required base:
`cursor/ir-compiler-phase16-6d81-979e` at
`dbfeb7816986ba886eb14e20c092f4f8f833a629`
([draft PR #104](https://github.com/gaoyu06/native-obfuscator/pull/104)).
Sol review [#105](https://github.com/gaoyu06/native-obfuscator/pull/105)
accepted that exact tip without a compiler change. The base is PR #104, not
`master`.

## Category model and validation

`I32`, `F32`, and `REFERENCE` are category 1 (one JVM slot). `I64` and `F64`
are category 2 (two JVM slots). The IR stack keeps one typed `IrValue` per JVM
value, so a category-2 value is never represented or manipulated as two
category-1 entries.

The stack-type pass and block value lowering call the same category-aware
transformation. Every required incoming entry is read and checked before the
first list insertion or removal. Stack underflow and every category sequence
not listed below reject the whole method at the shuffle opcode before C++
emission, cache allocation, native metadata emission, or `ACC_NATIVE`.

## Legal forms

In every table, `v1` is the incoming top of stack.

### `DUP2` (92)

| Form | Required input categories | Input stack | Output stack |
| --- | --- | --- | --- |
| 1 | `v2=cat1`, `v1=cat1` | `..., v2, v1` | `..., v2, v1, v2, v1` |
| 2 | `v1=cat2` | `..., v1` | `..., v1, v1` |

### `DUP_X2` (91)

| Form | Required input categories | Input stack | Output stack |
| --- | --- | --- | --- |
| 1 | `v3=cat1`, `v2=cat1`, `v1=cat1` | `..., v3, v2, v1` | `..., v1, v3, v2, v1` |
| 2 | `v2=cat2`, `v1=cat1` | `..., v2, v1` | `..., v1, v2, v1` |

### `DUP2_X1` (93)

| Form | Required input categories | Input stack | Output stack |
| --- | --- | --- | --- |
| 1 | `v3=cat1`, `v2=cat1`, `v1=cat1` | `..., v3, v2, v1` | `..., v2, v1, v3, v2, v1` |
| 2 | `v2=cat1`, `v1=cat2` | `..., v2, v1` | `..., v1, v2, v1` |

Opcode 93 is the measured phase-17 admission gap. A dedicated regression
first applies legal category-one `SWAP` and then form 1 of `DUP2_X1`.

### `DUP2_X2` (94)

| Form | Required input categories | Input stack | Output stack |
| --- | --- | --- | --- |
| 1 | `v4=cat1`, `v3=cat1`, `v2=cat1`, `v1=cat1` | `..., v4, v3, v2, v1` | `..., v2, v1, v4, v3, v2, v1` |
| 2 | `v3=cat1`, `v2=cat1`, `v1=cat2` | `..., v3, v2, v1` | `..., v1, v3, v2, v1` |
| 3 | `v3=cat2`, `v2=cat1`, `v1=cat1` | `..., v3, v2, v1` | `..., v2, v1, v3, v2, v1` |
| 4 | `v2=cat2`, `v1=cat2` | `..., v2, v1` | `..., v1, v2, v1` |

### `POP2` (88)

| Form | Required input categories | Input stack | Output stack |
| --- | --- | --- | --- |
| 1 | `v2=cat1`, `v1=cat1` | `..., v2, v1` | `...` |
| 2 | `v1=cat2` | `..., v1` | `...` |

## Fallback before mutation

The phase-17 fallback regression executes legal forms of every newly admitted
opcode, including `SWAP` followed by `DUP2_X1`, and then reaches a still
unsupported non-`int` primitive `NEWARRAY`. Rejection is reported at opcode
188 while generated output, native metadata, method access, and all caches
remain unchanged.

`NEWARRAY` forms outside the retained `int[]` slice, `MULTIANEWARRAY`,
primitive arrays beyond that slice, and `invokedynamic` remain unsupported.
No default-mode, evaluator, reader, or snippet-resource change is included.

## Verification

The focused suite is run with the required GNU C/C++ toolchain:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is an assertion-based
g++ gate, not an assumption-based skip. Its retained translation unit is also
checked independently with `g++ -std=c++17 -fsyntax-only` and the active JDK
JNI include directories.

The focused tests cover every legal form in the tables with mixed `I32`,
`F32`, `REFERENCE`, `I64`, and `F64` carriers as applicable; illegal category
mixes; the measured `SWAP` → `DUP2_X1` sequence; fallback-before-mutation; and
the retained phase-9 through phase-16 regressions.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` remains the CLI default gate, and the
no-mode `MethodProcessor.shouldProcess` overload remains a legacy-selection
gate. `Main` still declares `defaultValue = "legacy"`, and the public API
overload without a `CodegenMode` still delegates with `CodegenMode.LEGACY`.
`sources/cppsnippets.properties` remains present.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for all JVM-legal forms
  of `DUP2`, `DUP_X2`, `DUP2_X1`, `DUP2_X2`, and `POP2` as category-aware
  SSA stack transformations, including rejection before mutation. /
  直接 typed-CFG IR 支持 `DUP2`、`DUP_X2`、`DUP2_X1`、`DUP2_X2` 与
  `POP2` 的全部 JVM 合法形式，以 category-aware SSA 栈变换实现，并在
  mutation 前拒绝非法形式。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** /
  **否——尚未达到可发布状态。**
- **(c) Review focus / 审查重点:** Review all four `DUP2_X2` forms,
  category-2 value integrity, shared pre-mutation validation in both stack
  passes, and the measured `SWAP` → `DUP2_X1` path. /
  请重点审查 `DUP2_X2` 的四种形式、category-2 值的完整性、两个栈处理
  阶段共享的 mutation 前验证，以及实测的 `SWAP` → `DUP2_X1` 路径。
- **(d) Integration / 集成:** Base is PR #104 at `dbfeb781`, not `master`;
  re-run the focused suite with `CC=gcc CXX=g++`; preserve phase-9 through
  phase-16 regressions, the `legacy` default, and snippet resources. /
  基线是 `dbfeb781` 的 PR #104 而非 `master`；请使用
  `CC=gcc CXX=g++` 重新运行聚焦测试；保留 phase-9 至 phase-16
  回归、`legacy` 默认值和 snippet 资源。
