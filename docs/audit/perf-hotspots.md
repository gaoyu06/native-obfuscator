# Generated-code performance hotspots

No timings were collected or inferred. This ranks likely costs from generated C++ structure and JNI operation frequency.

## Highest likely costs

1. **`invokedynamic` bootstrap/link work appears on every execution of the original instruction.** The preprocessor emits bootstrap argument arrays, boxing, bootstrap invocation/linking, and method-handle invocation inline; no generated static call-site cache is visible (`bytecode/IndyPreprocessor.java:23-304`). On `STD_JAVA`/`ANDROID`, each execution creates `Object[]`, boxes primitive inputs/static arguments, calls the bootstrap, calls `CallSite.getTarget`, then `MethodHandle.invokeWithArguments`, and unboxes (`IndyPreprocessor.java:79-181,251-276,307-370`). HotSpot also builds arrays and calls internal `linkCallSite` before invoking the handle (`IndyPreprocessor.java:185-245,253-262`). Repeated string concatenation and lambdas are therefore prime hotspots.
2. **Java operations become JNI calls inside an already-native method.** Virtual/static/interface/special calls use `Call*Method`, fields use `Get/Set*Field`, and arrays use region/element APIs (`cppsnippets.properties:42-110,248-360,407-565`). This blocks normal JVM inlining and optimization across those operations, matching the README warning that broad conversion slows code (`README.md:6`).
3. **Per-native-call class/classloader discovery.** Every generated instance method calls `Object.getClass`, every method calls `Class.getClassLoader`, and instance methods then reload their declaring class through `ClassLoader.loadClass` (`MethodProcessor.java:163-180`; `native_jvm.cpp:239-277,304-318`). This occurs before executing the translated body, even if it only performs arithmetic.
4. **Per-bytecode exception polling.** Potentially throwing snippets append `ExceptionCheck`; generated class/method/field cache initialization also checks exceptions (`GenericInstructionHandler.java:34-49`; `cppsnippets.properties:42-110,132-149,210-231,242-246,248-360,407-574`; `FieldHandler.java:23-46`; `MethodHandler.java:183-208`). The checks are required for translated Java semantics but are frequent JNI dispatches.
5. **Object-reference bookkeeping and cleanup.** Generated methods construct `std::unordered_set<jobject> refs` per call, insert references after loads/returns/allocations, and clear references at frame boundaries (`MethodProcessor.java:211-233`; `cppsnippets.properties:9-11,41,58,78,210-228,256-300,443-565,567-570`; `FrameHandler.java:68-92`). `clear_refs` iterates the set, calls `GetObjectRefType` for each entry, then `DeleteLocalRef` (`native_jvm.cpp:328-333`). Hashing/allocation plus JNI inspection can dominate reference-heavy code.
6. **Primitive/object array access is JNI-granular.** Each translated `xaload`/`xastore` uses `Get/Set*ArrayRegion` for one element (or object element calls), and boolean/byte paths first call `IsInstanceOf` (`cppsnippets.properties:42-110`; `native_jvm.cpp:288-301`). Tight array loops cannot become native pointer loops.
7. **Multidimensional arrays recurse and populate via JNI one element at a time.** Helpers allocate each level and loop with `SetObjectArrayElement`, exception checks, and local-ref deletes (`native_jvm.cpp:180-237`; `native_jvm.hpp:31-76`).

## Caches: what is and is not per call

- Core `FindClass`/`GetMethodID` calls in `init_utils` occur during `JNI_OnLoad`, not for every translated method (`native_jvm.cpp:27-126`).
- Generated referenced classes are cached as weak globals. Each use still performs two `IsSameObject` checks on the warm path; a collected weak ref triggers a mutex, class loading, `NewWeakGlobalRef`, and local-ref deletion (`TypeHandler.java:15-25`; `FieldHandler.java:23-34`; `MethodHandler.java:183-194`).
- Field and method IDs are lazily cached, so `GetFieldID`/`GetMethodID` are not intended to run after warm-up. Every translated access/call still branches on the cache slot (`FieldHandler.java:36-46`; `MethodHandler.java:196-208`). Unlike class initialization, these writes have no mutex/atomic synchronization in generated code, which is also a concurrency correctness risk.
- String constants are interned and promoted to global references during class native registration, avoiding repeated `NewStringUTF`, but startup pays `NewStringUTF`, `String.intern`, `NewGlobalRef`, and deletions per cached string (`ClassSourceBuilder.java:84-92`; `native_jvm.cpp:335-340`).
- Exception construction calls `FindClass` every time `throw_re` runs; exception classes are not cached there (`native_jvm.cpp:279-286`).

## Other material costs

- JNI varargs `Call*Method` dispatch for every translated invocation; return-sort-specific snippets avoid Java wrapper boxing in ordinary calls but do not avoid JNI transitions (`cppsnippets.properties:395-565`).
- `IF_ACMP*`, `IFNULL`, and `IFNONNULL` use `IsSameObject` rather than raw pointer comparison (`cppsnippets.properties:201-202,232-233`).
- Catch dispatch uses `ExceptionOccurred`, `ExceptionClear`, and one or more `IsInstanceOf` tests (`cppsnippets.properties:242-246`; `MethodProcessor.java:263-307`).
- C++ is built with `-O2`, but JNI boundaries, observable exception state, and calls through `JNIEnv` function tables constrain optimizer visibility (`sources/CMakeLists.txt:9-14`; `zig/ZigBuilder.java:86-105`).

## Measurement priorities

Future benchmarks should isolate, without changing compiler behavior:

1. arithmetic-only translated call overhead versus original Java;
2. repeated lambda and Java 9+ string concatenation to expose repeated indy linking;
3. virtual-call and field-access loops after caches are warm;
4. primitive/object array loops and multidimensional allocation;
5. reference-heavy control flow with frequent stack-map frames;
6. first-call versus warm-call behavior, and class-cache repopulation after GC.

Report JNI call counts/allocation profiles and wall-clock distributions only after running a controlled benchmark; this audit intentionally supplies no invented timing.
