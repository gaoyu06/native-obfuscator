# JNI lookup cache notes

## Change

Generated class sources already shared `cmethods` and `cfields` slots, but
instance method and field instructions checked the weak class cache before
checking whether the member ID was warm. Consequently, every execution called
`JNIEnv::IsSameObject`, including loop iterations whose member ID had long
since been resolved.

The generator now:

- checks an instance member's ID slot first and moves class resolution plus
  `GetMethodID` or `GetFieldID` into that slot's cold path;
- stores member IDs in `std::atomic` slots with acquire/release publication;
- serializes each cold member lookup with its own mutex and checks the slot
  again under that mutex, preventing concurrent first executions from issuing
  duplicate JNI ID lookups;
- keeps the class-cache check outside the ID cold path for static fields,
  static methods, and nonvirtual calls because those JNI operations use the
  cached `jclass` on every execution.

Resolution remains lazy at the original bytecode instruction. The CLI and
selection behavior are unchanged, and Java main sources still target Java 8.

## Generated C++ evidence

A Java 8 smoke class with three accesses to one instance field produced this
shape on `master`:

```cpp
if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) {
    // resolve the class
}
if (!cfields[0]) {
    cfields[0] = env->GetFieldID(cclasses[0], "value", "I");
}
value = env->GetIntField(object, cfields[0]);
```

The same input after this change produces:

```cpp
if (!cfields[0].load(std::memory_order_acquire)) {
    std::lock_guard<std::mutex> lock(cfields_mtx[0]);
    if (!cfields[0].load(std::memory_order_relaxed)) {
        if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) {
            // resolve the class
        }
        cfields[0].store(
            env->GetFieldID(cclasses[0], "value", "I"),
            std::memory_order_release);
    }
}
value = env->GetIntField(
    object, cfields[0].load(std::memory_order_acquire));
```

Thus a warm instance-field instruction no longer calls `IsSameObject` and
cannot repeat `GetFieldID`; the method-ID path has the same structure. The
generated smoke source declared two mutex arrays and atomic slots:

```cpp
std::mutex cmethods_mtx[4];
std::atomic<jmethodID> cmethods[4];
std::mutex cfields_mtx[2];
std::atomic<jfieldID> cfields[2];
```

## Verification

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test`: main and test Java compilation
  succeeded, but no tests started because the existing runtime classpath lacks
  the JUnit Platform launcher.
- `CC=gcc CXX=g++ ./gradlew :obfuscator:assemble`: passed.
- Smoke transpile: passed for a Java 8 class containing an instance-field and
  method-call loop.
- Generated native build with GCC/G++ 13.3 and CMake 3.28.3: passed.
- Original and transformed smoke runs both printed `250000`.

## Benchmark measurement

The unchanged harness from
`origin/cursor/bench-harness-6d81` at `c239a84` was run in two detached
worktrees on Linux x86-64 with OpenJDK 21.0.10 and G++ 13.3. Each run used five
warmups and ten measured samples. Baseline was the harness commit; changed was
that commit plus `9888566`. Both complete runs passed transpilation, native
compilation, execution, kernel-set checks, and checksum checks.

Command:

```sh
CC=/usr/bin/gcc CXX=/usr/bin/g++ ./gradlew bench
```

Native results:

| Kernel | Baseline mean | Changed mean | Mean delta | Baseline median | Changed median |
| --- | ---: | ---: | ---: | ---: | ---: |
| integer-loop | 181,979,321 ns | 186,919,429 ns | +2.71% | 181,960,315 ns | 186,914,906 ns |
| string-concat-hash | 12,932,797 ns | 11,974,868 ns | -7.41% | 13,183,468 ns | 12,242,912 ns |
| recursion | 12,786,419 ns | 12,563,045 ns | -1.75% | 12,790,663 ns | 12,567,262 ns |

This is one local run per revision, so it is diagnostic rather than a release
threshold. The mixed result does not support a broad non-regression claim.
An initial invocation with relative `CC=gcc CXX=g++` stopped at CMake
configuration because the harness resolved `g++` under the worktree; it
produced no native measurement and is excluded from the table.
