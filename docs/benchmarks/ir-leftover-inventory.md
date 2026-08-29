# IR leftover inventory on current master

## Scope and interpretation

- Measured compiler base (merge-base with `origin/master`): `4214d7498c4b902d1dbf54f0bc14a3be16649b89`
- Measurement commit: `4214d7498c4b902d1dbf54f0bc14a3be16649b89`
- This is an admission measurement of checked-in fixtures with explicit `--codegen=ir`.
- This is **not a JDK support badge**, **not coverage-complete**, and not a behavioral/native E2E claim.
- This run changes no compiler/runtime source or defaults: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain the defaults.
- This report re-measures the post-#198 tree after #192–#198 added distinct-suffix extras, branches, switches, and hybrid suffix sets; two-level nested non-trapping inputs; and leaf-only `IDIV`/`IREM`. It supersedes the post-#190 inventory recorded by #191. #181 remains the earlier post-#180 snapshot.
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

Temurin 25 provisioning used the repository-documented archive and checksum:

```bash
curl -sSfL -o /tmp/jdk25.tar.gz "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz" && echo "dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e  /tmp/jdk25.tar.gz" | sha256sum -c && mkdir -p /tmp/temurin25 && tar xzf /tmp/jdk25.tar.gz -C /tmp/temurin25 --strip-components=1 && /tmp/temurin25/bin/javac -version
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

The helper reported zero measured leftovers in every corpus. This is not a complete JVM inventory.

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

## Remaining static reject paths in current docs

Zero measured leftovers in these fixtures is not a complete JVM inventory. The post-#198 [current-goal](../architecture/current-goal.md) documentation still lists these conservative reject paths; this is not a full JVM feature matrix:

| Static reject path | Area |
| --- | --- |
| Non-identity prefix `ASTORE 0` / receiver-alias forwarding | Constructor split |
| Unproven prefix → suffix jumps or switches | Constructor split |
| Other mixed prefix/suffix try/catch placements beyond #171/#184/#187/#188 | Constructor split |
| Remaining multi-super shapes: nested/`IDIV`-as-inner trees or three-or-more nested binaries | Constructor split |
| More than eight distinct multi-super paths | Constructor split |
| Extras unassigned on a bridge-taking path | Constructor split |
| Unsafe or unproven `ConstantDynamic` shapes (non-static, varargs, malformed, or cyclic) | IR frontend |
| Legacy subroutine bytecode (`jsr` / `ret`) | IR frontend |

## Next increment candidates from measured counts

No measured leftover exists, so this corpus does not support an evidence-based ordering of next increments.

This ordering is an admission-count observation for these fixtures only. It is not evidence of broader bytecode/JDK coverage, and it does not justify changing the `legacy` default.

## Raw evidence

- Exact method ledger: `/tmp/native-obfuscator-ir-leftover-inventory/methods.tsv`
- Per-fixture raw marker/log counts: `/tmp/native-obfuscator-ir-leftover-inventory/fixtures.tsv`
- Expanded command log: `/tmp/native-obfuscator-ir-leftover-inventory/commands.log`

Generated by `docs/measurement/ir-leftover-inventory/measure.py`; do not edit measured counts by hand.
