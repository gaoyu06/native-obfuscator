# Post-#168 IR leftover inventory / #168 后 IR 剩余项清单

## (a) Scope / 范围

Measurement-only refresh against exact `origin/master` compiler SHA
`74dc43310d9104028eb89dedd9530bcd88047471`, after #163–#168. The helper
inventories exact `javap` method identities and joins them to IR markers or
fallback diagnostics for the in-tree ClassicTest and JDK 17/21/25 fixtures.

本次仅更新测量，基于 #163–#168 落地后的精确 `origin/master` 编译器提交
`74dc43310d9104028eb89dedd9530bcd88047471`。辅助脚本清点 `javap`
方法，并按类名、方法名和描述符与 IR 标记或 fallback 诊断精确关联，覆盖仓内
ClassicTest 与 JDK 17/21/25 fixtures。

Fresh results from this run / 本次实测结果:

| Corpus | Inventoried | IR-admitted | Leftover/fallback | Missing join |
| --- | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 |
| JDK 17 fixtures | 82 | 82 | 0 | 0 |
| JDK 21 fixtures | 47 | 47 | 0 | 0 |
| JDK 25 fixtures | 21 | 21 | 0 | 0 |
| **Total** | **258** | **258** | **0** | **0** |

## (b) Ship-ready? / 可直接上线？

**No / 否。** Zero measured leftovers in these fixtures is not a JDK support
badge, behavioral/native E2E proof, or coverage-complete result.

这些 fixtures 中实测剩余项为零，并不代表 JDK 支持、行为/原生 E2E 正确性，
也不代表覆盖完整。

## (c) Review / 审查

Review the exact method join, generated counts, and separation between measured
leftovers and static source-visible reject paths. No compiler/runtime admission
code changed.

请审查精确方法关联、生成的计数，以及“实测剩余项”和“源码中静态拒绝路径”
的区分。编译器/运行时接纳逻辑未改动。

## (d) Preconditions and remaining rejects / 前置条件与剩余拒绝

The run used explicit `--codegen=ir`; defaults remain
`--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. There is **no
default flip**.

本次显式使用 `--codegen=ir`；默认值仍为 `--codegen=legacy`、
`--ir-lower=direct` 和 `--backend=cpp`，**没有切换默认值**。

Static reject paths still include retained-prefix non-identity `ASTORE 0`;
prefix-to-suffix branches other than an admitted join `GOTO`; mixed
prefix/suffix try/catch; non-diamond multi-super constructors; conditionally
assigned extras; unsafe/unproven non-static, varargs, malformed, or cyclic
`ConstantDynamic`; and `jsr` / `ret`.

源码中的静态拒绝路径仍包括：保留前缀中的非恒等 `ASTORE 0`；除已接纳 join
`GOTO` 之外的前缀到后缀分支；跨前缀/后缀的 try/catch；非菱形多 super
构造器；条件赋值 extra local；不安全或未证明的非静态、varargs、畸形或循环
`ConstantDynamic`；以及 `jsr` / `ret`。
