# IR leftover inventory on current master

## Scope and interpretation

- Measured compiler base (merge-base with `origin/master`): `51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- Measurement commit: `51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- This is an admission measurement of checked-in fixtures with explicit `--codegen=ir`.
- This is the [#391](https://github.com/gaoyu06/native-obfuscator/pull/391) leftover-docs tree at `51d6aedfc0f8c678950f8ee2c2b71f797377648d`: post-[#390](https://github.com/gaoyu06/native-obfuscator/pull/390) first-plus-second-plus-fifth five-argument `GregorianCalendar` `NEW` extra-local `int` leftover-docs plus the #391 inventory leftover-docs.
- This measurement supersedes [#391](https://github.com/gaoyu06/native-obfuscator/pull/391)'s measurement of the [#389](https://github.com/gaoyu06/native-obfuscator/pull/389) leftover-docs tree at `1db7af56ee6fc787977efbd2974ce070bc71a8da`.
- Latest compiler parent XML is **[#390](https://github.com/gaoyu06/native-obfuscator/pull/390) (677)** (`IrCompilerTest` 670 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- Processor changed: **No**.
- Admitted: **No** (measurement only).
- Ship-ready: **No**.
- This run changes no compiler/runtime source or defaults: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain the defaults.
- Inventory means `javap -p -s -c` methods with a `Code:` body. Results are joined by exact `class + method + descriptor`.
- `// IR codegen:` means IR; `falling back to legacy for this method` means `legacy-fallback`; `leaving constructor bytecode unchanged` means `constructor-left-java`.

## Host

`uname -a` (verbatim):

```text
Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
```

`java -version` (verbatim):

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
```

Compilers used:

| Release | Compiler | Version output |
| ---: | --- | --- |
| 8 | `javac` | `javac 21.0.10` |
| 17 | `javac` | `javac 21.0.10` |
| 21 | `javac` | `javac 21.0.10` |
| 25 | `/tmp/temurin25/bin/javac` | `javac 25.0.4.1` |

## Commands actually run

Top-level command:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The helper recorded every expanded per-fixture command in `/tmp/native-obfuscator-ir-leftover-inventory/commands.log`. The key commands were:

```bash
uname -a
java -version
./gradlew :obfuscator:shadowJar
# For every present fixture, with its matching N and compiler:
javac --release N -g -d <classes> <fixture Java sources>
jar --create --file <input.jar> -C <classes> .
javap -p -s -c -classpath <input.jar> <each input class>
java -jar obfuscator/build/libs/obfuscator.jar <input.jar> <output> --codegen=ir
```

No historical fixture branch was fetched. All source trees came from `obfuscator/test_data/tests/` on the measured checkout. `pull-requests/PullRequest72/TestStringConcatFactory.j` was not assembled; this measurement uses that fixture's Java source only, matching the 108-method Java ClassicTest corpus.

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

ClassicTest result from this run: **108/108 IR**, 0 legacy fallback, 0 constructor left in Java, and 0 missing.

## ClassicTest measured leftovers only

| Fixture | Class | Method | Descriptor | Category | Exact first reject/log reason |
| --- | --- | --- | --- | --- | --- |
| _None_ | — | — | — | — | — |

## jdk17 measured leftovers only

| Fixture | Class | Method | Descriptor | Category | Exact first reject/log reason |
| --- | --- | --- | --- | --- | --- |
| _None_ | — | — | — | — | — |

## jdk21 measured leftovers only

| Fixture | Class | Method | Descriptor | Category | Exact first reject/log reason |
| --- | --- | --- | --- | --- | --- |
| _None_ | — | — | — | — | — |

## jdk25 measured leftovers only

| Fixture | Class | Method | Descriptor | Category | Exact first reject/log reason |
| --- | --- | --- | --- | --- | --- |
| _None_ | — | — | — | — | — |

## Code-path leftovers (static source audit)

These are source-visible rejection paths, kept separate from the measured fixture leftovers. A zero measured count does not remove the code path.

| Leftover class | Source path | Static reject behavior |
| --- | --- | --- |
| Constructor retained-prefix non-identity `ASTORE 0` | `ConstructorSpecialMethodProcessor` | `Constructor prefix changes local 0 before the bridge` (opcode 58 / `ASTORE`). |
| Constructor prefix → suffix branch other than an admitted shared-suffix join `GOTO` | `ConstructorSpecialMethodProcessor` | `Constructor prefix branches across the this/super call` (jump/table/lookup-switch target outside retained prefix). |
| Mixed constructor prefix/suffix try/catch | `ConstructorSpecialMethodProcessor` | `Constructor exception regions may not cross the this/super split`. |
| Non-diamond multi-super constructor | `ConstructorSpecialMethodProcessor` | `Constructor chain calls do not share one suffix join` or the exactly-one-chain-call control-flow diagnostics. |
| Conditionally assigned constructor-prefix extra | `ConstructorSpecialMethodProcessor` | `Constructor prefix extra local <n> is not definitely assigned on every path reaching the this/super call`. |
| Non-static `ConstantDynamic` bootstrap | `DynamicConstantSupport` | `ConstantDynamic bootstrap is not REF_invokeStatic`. |
| Varargs `ConstantDynamic` bootstrap shape | `DynamicConstantSupport` | `ConstantDynamic bootstrap must take Lookup, String, Class, then one exact parameter per static argument`. |
| Malformed `ConstantDynamic` | `DynamicConstantSupport` | `Malformed ConstantDynamic descriptor` and related descriptor/type shape checks reject before resolver installation. |
| Cyclic `ConstantDynamic` | `DynamicConstantSupport` | Rejected before resolver installation; `Cyclic ConstantDynamic bootstrap arguments`. |
| Legacy subroutine bytecode (`jsr`/`ret`) | `AsmToIr` | `Unsupported instruction for phase-two IR` with opcode 168 (`JSR`) or 169 (`RET`). |

Additional exact constructor-split rejection messages found in `ConstructorSpecialMethodProcessor` are `A constructor IR body must be an instance method returning V`, `Constructor has no direct this/super constructor call`, `Constructor suffix jumps into its bytecode prefix`, and `Constructor switch targets its bytecode prefix`.

Other exact `DynamicConstantSupport` rejection messages found on this checkout remain conservative shape/placement checks:

- `MethodHandle/MethodType LDC is not lowerable by the IR frontend: <cause>`
- `ConstantDynamic interface companion cannot be placed safely: <cause>`
- `ConstantDynamic interface companion has no hidden-method pool`
- `ConstantDynamic interface companion requires a public interface`
- `ConstantDynamic interface companion is not supported for annotations`
- `ConstantDynamic interface companion requires class-file version 55`
- `ConstantDynamic interface resolver installation is incomplete`
- `ConstantDynamic bootstrap bridge name collides with an existing interface member`
- `Cyclic ConstantDynamic bootstrap arguments`
- `Malformed ConstantDynamic descriptor`
- `ConstantDynamic result is not a scalar or reference`
- `ConstantDynamic bootstrap is not REF_invokeStatic`
- `ConstantDynamic bootstrap must take Lookup, String, Class, then one exact parameter per static argument`
- `ConstantDynamic bootstrap return does not match its constant type`
- `Primitive Type is not a loadable bootstrap argument`
- `Unsupported ConstantDynamic bootstrap argument`
- `ConstantDynamic bootstrap argument does not match parameter <index>`
- `ConstantDynamic resolver name collides with an existing class member`
- `Malformed MethodType LDC`
- `Unsupported MethodHandle LDC`

## Next increment candidates from measured counts

No measured leftover exists, so this corpus does not support an evidence-based ordering of next increments.

This ordering is an admission-count observation for these fixtures only. It is not evidence of broader bytecode/JDK coverage, and it does not justify changing the `legacy` default.

## Raw evidence

- Exact method ledger: `/tmp/native-obfuscator-ir-leftover-inventory/methods.tsv`
- Per-fixture raw marker/log counts: `/tmp/native-obfuscator-ir-leftover-inventory/fixtures.tsv`
- Expanded command log: `/tmp/native-obfuscator-ir-leftover-inventory/commands.log`

Generated by `docs/measurement/ir-leftover-inventory/measure.py`; do not edit measured counts by hand.
