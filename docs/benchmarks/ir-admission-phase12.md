# Phase 12 opt-in IR admission measurement

## Scope

This is a documentation-only measurement of the optional `--codegen=ir` path
on the compiler/runtime tip from
[`cursor/ir-phase12-sol-review-6d81`](https://github.com/gaoyu06/native-obfuscator/pull/89)
at `481b7b108388380bfbbdf94703ee56eb4b601b02` (draft PR #89). The
measurement sources were present at
`fae9d56585aceab661f7098d4e092b23e7918813`.

The result describes one controlled input on one tip. It is not a claim about
production coverage, the wider fixture corpus, runtime parity, or performance.
In particular, no speedup was measured or claimed.

## Corpus

There was no checked-in fixture JAR to run directly. The named input
`ir-admission-phase12-input.jar` was therefore compiled with `--release 8`
from these measurement-only sources:

- `docs/measurement/ir-admission-phase12/src/measurement/phase12/AdmissionTarget.java`
- `docs/measurement/ir-admission-phase12/src/measurement/phase12/IntOperation.java`

The JAR contains exactly these input classes:

- `measurement/phase12/AdmissionTarget.class`
- `measurement/phase12/IntOperation.class`

Observed SHA-256:
`6a46c4d65d7aa9e8c49cefca82a221207522310049aaa53d7a3571e1b3e800e3`.
The corpus covers a constructor and `I`/`J`/reference field stores, field reads,
an interface default method, an `INVOKEINTERFACE` call, and a method containing
the unsupported `I2F` opcode. It does not include or claim to represent JDK 17
E2E fixtures.

## Toolchain and commands

Measurement date: `2026-08-29` UTC.

- OS: `Linux 6.12.94+ x86_64 GNU/Linux`
- Java runtime: OpenJDK `21.0.10`
- `javac`: `21.0.10`
- `jar`: `21.0.10`
- Gradle wrapper: `9.3.1`

Commands were run from the repository root:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks

rm -rf /tmp/native-obfuscator-ir-admission-phase12
mkdir -p /tmp/native-obfuscator-ir-admission-phase12/classes
javac --release 8 \
  -d /tmp/native-obfuscator-ir-admission-phase12/classes \
  docs/measurement/ir-admission-phase12/src/measurement/phase12/IntOperation.java \
  docs/measurement/ir-admission-phase12/src/measurement/phase12/AdmissionTarget.java
jar --create \
  --date=2026-08-29T00:00:00Z \
  --file /tmp/native-obfuscator-ir-admission-phase12/ir-admission-phase12-input.jar \
  -C /tmp/native-obfuscator-ir-admission-phase12/classes .
jar --list \
  --file /tmp/native-obfuscator-ir-admission-phase12/ir-admission-phase12-input.jar

javap \
  -classpath /tmp/native-obfuscator-ir-admission-phase12/ir-admission-phase12-input.jar \
  -p -c -s \
  measurement.phase12.IntOperation \
  measurement.phase12.AdmissionTarget

rm -rf /tmp/native-obfuscator-ir-admission-phase12/output
set -o pipefail
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/native-obfuscator-ir-admission-phase12/ir-admission-phase12-input.jar \
  /tmp/native-obfuscator-ir-admission-phase12/output \
  --codegen=ir 2>&1 |
  tee /tmp/native-obfuscator-ir-admission-phase12/ir-run.log
```

The input-method count was obtained from `javap`:

```bash
javap \
  -classpath /tmp/native-obfuscator-ir-admission-phase12/ir-admission-phase12-input.jar \
  -p -s measurement.phase12.IntOperation measurement.phase12.AdmissionTarget |
  rg '^  (public|protected|private).*\);$' |
  wc -l
```

Output: `6`.

IR admission was counted from the compiler's generated
`// IR codegen: <class>.<name><descriptor>` markers, restricted to the six
named input methods:

```bash
rg 'IR codegen: measurement/phase12/.*\.(<init>|increment|total|call|unsupported|apply)\(' \
  /tmp/native-obfuscator-ir-admission-phase12/output/cpp/output/*.cpp |
  wc -l
```

Output: `5`.

Fallback was counted from the per-method compiler diagnostics:

```bash
rg 'IR codegen unsupported for measurement/phase12/' \
  /tmp/native-obfuscator-ir-admission-phase12/ir-run.log |
  wc -l
```

Output: `1`.

The tool injects `<clinit>()V` methods after reading the input classes. Those
generated methods are not input-corpus methods and are excluded from both the
inventory and admission count.

## Per-method result

| Input class | Method + descriptor | Exercised shape | Result | Logged fallback reason |
| --- | --- | --- | --- | --- |
| `measurement/phase12/AdmissionTarget` | `<init>(IJLmeasurement/phase12/IntOperation;)V` | constructor; `I`, `J`, and reference field stores | IR | — |
| `measurement/phase12/AdmissionTarget` | `increment(I)I` | integer field read/write | IR | — |
| `measurement/phase12/AdmissionTarget` | `total()J` | long field read and return | IR | — |
| `measurement/phase12/AdmissionTarget` | `call(I)I` | reference field read and interface call | IR | — |
| `measurement/phase12/AdmissionTarget` | `unsupported(I)I` | `I2F`, float constant/add, and `F2I` | fallback | `Unsupported instruction for phase-two IR at bytecode instruction 3 (opcode 134)` |
| `measurement/phase12/IntOperation` | `apply(I)I` | interface default method | IR | — |

The complete compiler line was:

```text
IR codegen unsupported for measurement/phase12/AdmissionTarget#unsupported(I)I: Unsupported instruction for phase-two IR at bytecode instruction 3 (opcode 134); falling back to legacy for this method
```

## Observed counts

- Input methods: **6**
- IR: **5**
- Per-method fallback: **1**
- Observed admission fraction: **5/6** (`83.3%`, calculated with
  `awk 'BEGIN { printf "%.1f%%\n", 100 * 5 / 6 }'`)

These counts are only for the exact two-class JAR above on the draft PR #89
tip. They must not be read as production IR coverage.
