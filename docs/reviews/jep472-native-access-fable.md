# Fable review: JEP 472 native-access packaging (#145)

- **Scope reviewed:** PR #145 only — branch `cursor/jep472-native-access-6d81`,
  tip `47661918a75eef4c8489f88596dfb5e59bb2f5dc`, diffed against `origin/master`.
- **Verdict:** **accept-with-nits**
- **Ship-ready:** **No** (unchanged from the implementation PR's own claim; this
  is a packaging/docs increment, not a "JDK 25 supported" milestone).

## What was checked (independently, not from the PR body)

### 1. Diff surface matches the claim

`git diff --stat origin/master...47661918` touches exactly six files:

- `obfuscator/src/main/java/by/radioegor146/NativeObfuscator.java` (+33/−6,
  manifest write path only)
- `obfuscator/src/test/java/by/radioegor146/ManifestNativeAccessTest.java` (new)
- `README.md` (usage note under "Native-access on JDK 24+")
- `docs/architecture/compatibility-jdk.md` (JNI native-access row only)
- `docs/architecture/jep472-native-access.md` (new)
- `PR_BODY.md`

No constructor-split, interpreter, evaluator, or IR-frontend files are touched.
The `NativeObfuscator.java` change is confined to the output-JAR manifest write
and the new `buildOutputManifest` helper; the entry-copy loop, loader class
emission, and `System.load` / `System.loadLibrary` call sites are unchanged.

### 2. CLI defaults unchanged

`Main.java` on the branch still declares `--codegen` `defaultValue = "legacy"`,
`--ir-lower` `defaultValue = "direct"`, `--backend` `defaultValue = "cpp"`.
`CodegenModeTest.cliDefaultsToLegacy` and `cliDefaultsToDirectIrLowering` pass.
The README addition explicitly states, verbatim, *"This is a packaging
convenience, not a claim that JDK 25 is supported"*, and no support-badge
language was added anywhere.

### 3. Manifest helper semantics (read from source, lines 604–614)

`buildOutputManifest(Manifest inputManifest)`:

- `null` input → `new Manifest()`, then `Manifest-Version: 1.0` is added (guard:
  only when absent) and `Enable-Native-Access: ALL-UNNAMED` is added (guard:
  only when absent). Minimal manifest, as claimed.
- Non-null input → deep-copied via `new Manifest(inputManifest)`, so **all**
  input main attributes (`Main-Class`, etc.) carry through.
- Any pre-existing `Enable-Native-Access` value (e.g. `org.example.mod`) is
  preserved because the put is guarded by `getValue(...) == null`. No overwrite.
- Duplicate-entry safety: the entry-copy loop skips `META-INF/MANIFEST.MF`
  (line 285, pre-existing), so the unconditional manifest write at line 576
  cannot collide with a copied input entry.

### 4. Tests re-run — real counts

Command (OpenJDK 21.0.10, this review environment):

```
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ManifestNativeAccessTest
```

Result: `BUILD SUCCESSFUL`. From the JUnit XML:

- `by.radioegor146.CodegenModeTest` — tests=7, failures=0, errors=0, skipped=0
- `by.radioegor146.ManifestNativeAccessTest` — tests=4, failures=0, errors=0,
  skipped=0

The four new tests genuinely assert the four claimed cases: (1) full `process`
run emits the attribute and preserves `Main-Class`; (2) full run with no input
manifest yields a minimal manifest with the attribute; (3) an existing specific
`Enable-Native-Access` value survives `buildOutputManifest`; (4) `null` input
yields `Manifest-Version: 1.0` + attribute and no `Main-Class`. Two tests
exercise the real `process` path end-to-end (reading the manifest back out of
the written output JAR), two unit-test the helper.

### 5. No claim that the #141 warning is gone

`docs/architecture/jep472-native-access.md` states the JDK 25 E2E result in
`docs/benchmarks/ir-jdk25-e2e-corpus.md` (commit `5ac6ec5`, verified present on
the branch and containing the four-line warning transcript) "was not re-run and
is not restated as resolved." The PR body says the same and records OpenJDK 21
as the test environment. README and the compatibility table both state that
classpath launches still require `--enable-native-access=ALL-UNNAMED`. Correct.

### 6. JEP 472 semantics stated accurately

Per JEP 472, the `Enable-Native-Access` manifest attribute is honored for the
application JAR launched via `java -jar`; it is not consulted for JARs on the
classpath of a `java -cp` launch. All three docs (README, compatibility row,
new architecture doc) state exactly this split and do not overclaim. The
`ALL-UNNAMED` value matches what the JDK 25 E2E warning itself suggested.

## Nits (concrete, non-blocking)

1. **Manifest is the last entry in the output JAR, not the first.** The write
   happens after all class/resource entries (line 576, pre-existing ordering
   inherited from master). `java -jar` reads via `JarFile` (central directory),
   so the attribute is honored, and the new tests confirm that path. But
   stream-based consumers (`JarInputStream.getManifest()`, some repackaging
   tools) only recognize a manifest appearing as the first entry. Now that the
   manifest carries a semantically meaningful attribute, a follow-up that writes
   `META-INF/MANIFEST.MF` first would make the packaging more conventional.
   Not a regression introduced by this PR.
2. **Test fixture uses a non-conventional `Main-Class` value.**
   `ManifestNativeAccessTest` sets `Main-Class: example/Program` (slash form);
   real manifests use the dotted form (`example.Program`). The assertion is a
   round-trip preservation check so the test is still valid, but the fixture
   would be more faithful with the dotted name.

## Not covered (correctly scoped out, restated for the record)

- The JDK 25 E2E was not re-run; whether the warning disappears under
  `java -jar` on JDK 24+/25 with this manifest is expected per JEP 472 but not
  empirically demonstrated in this increment.
- Classpath launches still warn without the flag; that is documented, not fixed.
- No native compile/link was exercised by the two re-run test classes.
