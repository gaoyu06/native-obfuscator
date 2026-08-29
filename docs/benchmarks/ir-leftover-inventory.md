# IR leftover inventory on current master

## Scope and interpretation

- Measured compiler base (merge-base with `origin/master`): `08bbcf45803fb2f9b28bdbf6880d4093a16d923f`
- Measurement commit: `bd8120ef0f55d28d2f78b2e42b03e6fb9655c464`
- This is an admission measurement of checked-in fixtures with explicit `--codegen=ir`.
- This is **not a JDK support badge** or a behavioral/native E2E claim.
- The CLI default is still **`legacy`**; this run did not change it.
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
| 25 | `/tmp/native-obfuscator-jdk25-5883/jdk-25.0.4.1+1/bin/javac` | `javac 25.0.4.1` |

## Commands actually run

Top-level command:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/native-obfuscator-jdk25-5883/jdk-25.0.4.1+1/bin/javac
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
| Constructor `ASTORE 0` in retained prefix | `ConstructorSpecialMethodProcessor` | `Constructor prefix changes local 0 before the bridge` (opcode 58 / `ASTORE`). |
| Constructor prefix → suffix branch | `ConstructorSpecialMethodProcessor` | `Constructor prefix branches across the this/super call` (jump/table/lookup-switch target outside retained prefix). |
| Constructor cross-split catch region | `ConstructorSpecialMethodProcessor` | `Constructor exception regions may not cross the this/super split`. |
| Multiple direct `this`/`super` candidates | `ConstructorSpecialMethodProcessor` | `Constructor has multiple possible this/super calls` (opcode 183 / `INVOKESPECIAL`). |
| Unforwarded constructor-prefix extra local | `ConstructorSpecialMethodProcessor` → `AsmToIr` | The bridge forwards initialized `this` plus declared constructor arguments, not other prefix-created locals; a suffix read reaches `Read of a local not defined on every incoming edge` with the read opcode. |
| Unsafe/unproven `ConstantDynamic` shape | `DynamicConstantSupport` | Rejected before resolver installation; exact source messages are listed below. |
| Legacy subroutine bytecode (`jsr`/`ret`) | `AsmToIr` | `Unsupported instruction for phase-two IR` with opcode 168 (`JSR`) or 169 (`RET`). |

Additional exact constructor-split rejection messages found in `ConstructorSpecialMethodProcessor` are `A constructor IR body must be an instance method returning V`, `Constructor has no direct this/super constructor call`, `Constructor suffix jumps into its bytecode prefix`, and `Constructor switch targets its bytecode prefix`.

Exact `DynamicConstantSupport` rejection messages found on this checkout:

- `MethodHandle/MethodType LDC is not lowerable by the IR frontend: <cause>`
- `ConstantDynamic LDC in an interface cannot use the IR resolver cache`
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
