# Compact blinded comparison run

## Scope and order

- Base: `origin/cursor/opcode-compact-encoding-6d81` at `be53efe`
- Evaluation branch: `cursor/eval-compact-blinded-6d81`
- Generated trees committed at `4da75a7`
- Opcode-only recovery committed at `ac521d4`
- Direct-only recovery committed at `acfadf1`
- Fixture construction opened only after both recovery commits

The fixture has no standalone Java source file. The integration test constructs
`fixture.jar` with ASM in `writeFixtureJar`; that Java test implementation was
not opened until the recovery gate had passed.

## Generation

The initial trees were produced by the integration test's default
`NativeObfuscator.process` overload and its `CompilerBackend.INTERPRETER` call.
After the recovery gate, the same preserved `fixture.jar` was also processed
through the command-line interface:

```sh
java -jar obfuscator/build/libs/obfuscator.jar \
  "$ROOT/fixture.jar" .run-tmp/cli-direct

java -jar obfuscator/build/libs/obfuscator.jar \
  --backend=interpreter \
  "$ROOT/fixture.jar" .run-tmp/cli-opcodes
```

Recursive diffs confirmed that both CLI `cpp/` trees were byte-for-byte equal
to the committed trees:

```text
docs/eval/compact-blinded/direct/
docs/eval/compact-blinded/opcodes/
```

The integration test
`defaultCppOutputIsUnchangedAndInterpreterFallsBackPerMethod()` also passed.

## Compile

Environment:

```text
OpenJDK 21.0.10
CMake 3.28.3
GCC/G++ 13.3.0
```

The environment's default `c++` selected Clang and initially failed its link
probe because it could not locate `libstdc++`. Configuring with the installed
GCC binaries resolved the toolchain issue:

```sh
CC=/usr/bin/gcc CXX=/usr/bin/g++ \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
cmake -S docs/eval/compact-blinded/direct -B .run-tmp/direct-build-gcc
cmake --build .run-tmp/direct-build-gcc --config Release

CC=/usr/bin/gcc CXX=/usr/bin/g++ \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
cmake -S docs/eval/compact-blinded/opcodes -B .run-tmp/opcodes-build-gcc
cmake --build .run-tmp/opcodes-build-gcc --config Release
```

Both targets built `libnative_library.so` successfully. The opcode target
compiled `native_jvm_interp.cpp` as part of the generated tree.

## Runtime comparison

Each library was added to its generated fixture JAR as
`native0/x64-linux.so`. A separate runner invoked:

```java
DemoKernel.add(7, 5);
DemoKernel.sumTo(10);
DemoKernel.mix(123456789, 7);
DemoKernel.divide(84, 7);
```

The original input, direct output, and opcode output all exited successfully
and printed exactly:

```text
add=12
sumTo=45
mix=-1506094324
divide=12
```

An exact string comparison across all three outputs passed.

## Shape observed

The opcode tree uses compact generic byte arrays for `add`, `sumTo`, and `mix`;
they are not named-opcode arrays. Reading them requires
`native_jvm_interp.cpp`'s decode table. `divide` is unsupported by that lowering
and remains a per-method `jvalue cstack` body. The direct tree uses per-method
`jvalue cstack`/`clocal` bodies for all four methods.
