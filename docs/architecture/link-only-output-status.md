# Link-only native output status

Status recorded on 2026-08-28 from
`cursor/link-only-native-output-6d81-a2de`, based on
`cursor/opcode-compact-encoding-6d81` at `be53efe`.

## CLI and output contract

The new flag is `--publish-native-lib`. It requires
`--backend=interpreter`.

```text
java -jar native-obfuscator.jar \
  --backend=interpreter \
  --publish-native-lib \
  [--opcode-seed=<long>] \
  <input.jar> <empty-output-directory>
```

Without `--use-zig`, this configures and links a host shared library with
CMake. With `--use-zig`, the requested Zig targets are linked instead. Source
generation and all compiler intermediates live in a temporary build directory,
which is deleted after successful publication. The output directory receives
the transformed JAR and root-level LoaderUnpack names such as
`x64-linux.so`; the same library name is also present under the generated
LoaderUnpack directory in the JAR.

The publication output directory must be absent or empty. This makes the
published file set explicit and prevents files from an earlier source-emission
run from remaining in it.

The existing behavior is unchanged when the flag is absent. `cpp` remains the
default backend, and interpreter source-emission mode still writes the complete
`cpp/` tree.

## Per-translation opcode assignment

Each interpreter translation creates one byte assignment shared by the method
emitter and its generated dispatcher. `--opcode-seed=<long>` makes that
assignment reproducible. If no seed is supplied, a random 64-bit seed is
created and logged.

The generated dispatcher switches directly on numeric byte values. The
checked-in dispatcher template and generated dispatcher contain neither a
semantic opcode enum nor a byte-to-semantic-name table.

## Real verification

The following targeted command was run with GCC/G++ 13.3.0, CMake 3.28.3, and
OpenJDK 21:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.NativeLibraryPublicationTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --rerun-tasks --console=plain
```

Result: `BUILD SUCCESSFUL` with 14 tests, 0 skipped, 0 failures, and 0
errors.

| Required evidence | Test |
|---|---|
| No new flag: full interpreter C++ tree is emitted | `interpreterWithoutPublicationFlagStillEmitsCppTree` |
| New flag: shared library and JAR are published, with no `.cpp` file | `publishesLinkedLibraryWithoutInterpreterSourcesAndMatchesJavaOracle` |
| Published JAR executes with the linked library and matches Java output | `publishesLinkedLibraryWithoutInterpreterSourcesAndMatchesJavaOracle` |
| Equal seeds reproduce assignments; different seeds change them | `seededAssignmentsAreStableDistinctAndSharedWithDispatcher` |
| No option and explicit `cpp` produce byte-identical C++ trees | `defaultCppOutputIsUnchangedAndInterpreterFallsBackPerMethod` |

The publication integration test linked the fixture with CMake and g++, then
ran both the original fixture JAR and the published JAR. Both printed `4:45`.

The following commands were also run against that test's real published
directory:

```text
PUBLISHED=/tmp/native-library-publication-6169178007020446365/published
ls -la "$PUBLISHED"
jar tf "$PUBLISHED/fixture.jar" |
  rg 'native_jvm_interp\.cpp|native0/x64-linux\.so'
unzip -p "$PUBLISHED/fixture.jar" native0/x64-linux.so |
  cmp - "$PUBLISHED/x64-linux.so"
java -jar "$PUBLISHED/fixture.jar"
```

Observed output:

```text
fixture.jar
x64-linux.so
native0/x64-linux.so
4:45
```

The root library and JAR entry compared byte-for-byte equal. These source
absence assertions were run separately and exited with status 0:

```text
test ! -e "$PUBLISHED/cpp/native_jvm_interp.cpp"
! jar tf "$PUBLISHED/fixture.jar" | rg -q 'native_jvm_interp\.cpp'
! rg --files "$PUBLISHED" | rg -q '\.cpp$'
```
