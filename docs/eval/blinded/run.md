# Blinded readability run

## Scope and order

- Base: `origin/cursor/opcode-mix-lowering-6d81`
- Evaluation branch: `cursor/eval-blinded-reader-6d81`
- Subject: compiler/readability comparison of two generated C++ artifacts from
  the same compiled class
- Sample size: `N=1`

Reader order was preserved for the source oracle:

1. The opcode C++ and interpreter tables were read, and
   `recovery-opcodes.md` was committed as `c15c095`.
2. The direct C++ was read, and `recovery-direct.md` was committed as
   `aea5564`.
3. Only then was the Java test fixture construction opened and compared with
   both recoveries.

The branch has no `DemoKernel.java`. The existing integration fixture builds
`DemoKernel.class` directly with ASM. The focused test was used to create the
compiled input without opening its Java construction before the recoveries.

## Generation commands

```bash
JUNIT_JUPITER_TEMPDIR_CLEANUP_MODE_DEFAULT=NEVER \
  ./gradlew :obfuscator:test \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --rerun-tasks

cp /tmp/native-jvm-interpreter-integration-5399938890678466625/fixture.jar \
  docs/eval/blinded/fixture-input.jar

./gradlew :obfuscator:shadowJar

java -jar obfuscator/build/libs/obfuscator.jar \
  docs/eval/blinded/fixture-input.jar \
  docs/eval/blinded/direct

java -jar obfuscator/build/libs/obfuscator.jar \
  --backend=interpreter \
  docs/eval/blinded/fixture-input.jar \
  docs/eval/blinded/opcodes
```

The default command is the `cpp` backend. Both commands consumed the same
`fixture-input.jar`.

## Fallback gate

Fallback: **No** for the interesting `mix(int,int)` method. Its opcode artifact
contains an `_interp_code` table and calls `native_jvm::interp::execute_i`; its
method section does not contain `jvalue cstack`.

The separate `divide(int,int)` method does contain a direct per-method C++ body,
as expected for its unsupported instruction. That does not trigger the stated
stop condition, which applies to the interesting method.

## Build and same-output check

```bash
cmake -S docs/eval/blinded/direct/cpp \
  -B /tmp/blinded-direct-final-build \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake --build /tmp/blinded-direct-final-build --config Release -j2

cmake -S docs/eval/blinded/opcodes/cpp \
  -B /tmp/blinded-opcodes-final-build \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake --build /tmp/blinded-opcodes-final-build --config Release -j2

mkdir -p /tmp/blinded-final/direct/native0 \
  /tmp/blinded-final/opcodes/native0 \
  /tmp/blinded-final/classes
cp docs/eval/blinded/direct/fixture-input.jar /tmp/blinded-final/direct.jar
cp docs/eval/blinded/opcodes/fixture-input.jar /tmp/blinded-final/opcodes.jar
cp /tmp/blinded-direct-final-build/build/lib/libnative_library.so \
  /tmp/blinded-final/direct/native0/x64-linux.so
cp /tmp/blinded-opcodes-final-build/build/lib/libnative_library.so \
  /tmp/blinded-final/opcodes/native0/x64-linux.so
jar uf /tmp/blinded-final/direct.jar \
  -C /tmp/blinded-final/direct native0/x64-linux.so
jar uf /tmp/blinded-final/opcodes.jar \
  -C /tmp/blinded-final/opcodes native0/x64-linux.so
javac -d /tmp/blinded-final/classes docs/eval/blinded/BlindedRunner.java

java -cp /tmp/blinded-final/classes:/tmp/blinded-final/direct.jar \
  BlindedRunner > /tmp/blinded-final/direct.txt
java -cp /tmp/blinded-final/classes:/tmp/blinded-final/opcodes.jar \
  BlindedRunner > /tmp/blinded-final/opcodes.txt
cmp /tmp/blinded-final/direct.txt /tmp/blinded-final/opcodes.txt
```

Both artifacts printed the same numbers:

```text
4
45
307157386
9
```

## Contamination status

Contamination: **Yes, non-source protocol contamination.** Before writing the
recoveries, a query intended to locate only a build command in
`docs/architecture/opcode-mix-status.md` also exposed its title and lines
25–27. Those lines named `add`, `sumTo`, `mix`, and an unsupported integer
division method. The focused test's display name also announced that a
per-method fallback exists.

No Java fixture source, constants, expression sequence, or control flow was
seen before both recoveries were committed. Therefore the explicit
early-Java-source abort condition was not triggered, and scoring was completed.
The protocol violation still means this is not a clean blinded run.

## Result and limitation

Both artifacts scored four `full` recoveries and no `partial` or `fail`
recoveries. H0 (equal recovery) is **not rejected**.

This `N=1` tool-assisted comparison is not a scientific unaided-bar pass,
regardless of the equal scores. The contamination above further prevents an
unaided claim.
