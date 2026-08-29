# Code-generation pipeline audit

> **Historical audit** of `master` @ `e7ca4c8`. The current tree still has the
> legacy snippet path as the CLI default, plus `--codegen=ir`. Constructors can
> be admitted on IR; rejected `<init>` bodies are restored from original
> bytes. Classfiles are no longer unconditionally stamped to major 52. See
> [../architecture/project-status.md](../architecture/project-status.md).

Scope: source at commit `e7ca4c87deca403f692698fd74652d856f3c162f`. This is an
implementation audit of that commit, not a design promise and not a description
of current `master`.

## End-to-end path

1. `NativeObfuscator` constructs one process-wide `StringPool`, `Snippets`, four `NodeCache` instances, and a `MethodProcessor` (`obfuscator/src/main/java/by/radioegor146/NativeObfuscator.java:41-48,86-94`). `Snippets` loads Java `Properties` from `sources/cppsnippets.properties` (`Snippets.java:16-25`).
2. `process(...)` copies the fixed runtime sources (`native_jvm.cpp/.hpp`, `native_jvm_output.hpp`, `string_pool.hpp`) and prepares CMake/main-source builders (`NativeObfuscator.java:115-138`).
3. Every selected class is parsed to an ASM 9 `ClassNode`; only non-abstract, non-native, non-constructor methods are eligible (`NativeObfuscator.java:196-226`; `MethodProcessor.java:92-96`). Constructors therefore remain JVM bytecode.
4. `PreprocessorRunner` applies `IndyPreprocessor`, then `LdcPreprocessor` (`bytecode/PreprocessorRunner.java:12-22`). ASM recomputes frames/maxima and reparses the result before C++ generation (`NativeObfuscator.java:228-235`).
5. `IndyPreprocessor` replaces every `InvokeDynamicInsnNode` with ordinary instructions plus synthetic “magic” calls (`IndyPreprocessor.java:16-304,374-381`). HotSpot uses `MethodHandleNatives.linkCallSite`; standard-Java/Android invoke the bootstrap and then `MethodHandle.invokeWithArguments` (`IndyPreprocessor.java:27-248,251-279`). `LdcPreprocessor` lowers method-handle and method-type constants (`LdcPreprocessor.java:10-39`; `MethodHandleUtils.java:36-120`).
6. A `MethodContext` owns the output buffers, simulated stack/local state, active catches, labels, and references to shared caches (`MethodContext.java:13-39,41-85`). `MethodProcessor.processMethod` chooses the special processor, constructs the JNI function signature, creates `jvalue` stack/local slots, and walks ASM nodes in order (`MethodProcessor.java:108-173,184-259`).
7. The node type indexes a complete 16-slot ASM handler table: opcode nodes and metadata nodes have separate handlers (`MethodProcessor.java:55-79`). Opcode names are populated reflectively from public `Opcodes` integer fields (`MethodProcessor.java:17-27`), so this key selection depends on the final value stored for constants that share an integer. `GenericInstructionHandler` derives that name, current source line, catch fragment, return type, and stack-index tokens, lets the concrete handler add tokens/prefix code, then expands the selected snippet (`GenericInstructionHandler.java:19-61`).
8. After each node, its handler updates a compile-time stack pointer. Frames update logical local/stack types and emit JNI-local-reference cleanup (`MethodProcessor.java:252-259`; `instructions/FrameHandler.java:17-116`). Catch dispatch blocks are emitted after the ordinary body (`MethodProcessor.java:263-307`).
9. Special processors mutate the output class. Normal methods become native and lose their instructions; interface methods proxy through synthetic helper classes (`special/DefaultSpecialMethodProcessor.java:16-64`). `<clinit>` becomes registration plus a helper call (`special/ClInitSpecialMethodProcessor.java:12-40`). The output class version is unconditionally set to Java 8 / major 52 (`NativeObfuscator.java:283-287`).
10. `ClassSourceBuilder` writes one `.cpp/.hpp` pair per processed class, cache arrays, JNI registration, and generated functions (`source/ClassSourceBuilder.java:29-84,84-140`). `MainSourceBuilder`, `StringPool`, and `CMakeFilesBuilder` expand whole-file resource templates (`MainSourceBuilder.java:34-41`; `StringPool.java:73-92`; `CMakeFilesBuilder.java:35-43`). Final files are written at `NativeObfuscator.java:410-415`.

## Exact template semantics

There are two replacement layers:

- Whole-file templates use `Util.dynamicFormat`: only `$name` keys supplied by the builder are replaced (`Util.java:38-55`). This expands:
  - `sources/CMakeLists.txt` (`CMakeFilesBuilder.java:35-43`);
  - `sources/native_jvm_output.cpp` (`MainSourceBuilder.java:34-41`);
  - `sources/string_pool.cpp` (`StringPool.java:73-92`).
- Opcode snippets use `Snippets.getSnippet` and `Util.dynamicRawFormat` (`Snippets.java:41-65`; `Util.java:57-64`):
  1. load the property named by the derived instruction key;
  2. read optional `<key>_S_VARS`;
  3. resolve `#NAME` from `<key>_S_CONST_NAME`;
  4. resolve `$name` from handler tokens;
  5. intern every declared `#`/`$` string variable through `StringPool.get`;
  6. add all remaining `$token` values without interning;
  7. replace the raw token spellings.

For example, `IALOAD` declares `#NPE,#ERROR_DESC`; those literals become pointers into the generated modified-UTF-8 pool while `$line`, `$stackindexm2`, and `$trycatchhandler` remain generated C++ (`obfuscator/src/main/resources/sources/cppsnippets.properties:42-45`). A missing snippet fails at `Objects.requireNonNull(value, key)` (`Snippets.java:41-44`); there is no fallback implementation.

Concrete handlers may prepend cache initialization before snippet expansion. Type, field, method, and class-LDC handlers lazily populate weak-global class references and JNI IDs (`TypeHandler.java:12-28`; `FieldHandler.java:14-46`; `MethodHandler.java:151-210`; `LdcHandler.java:58-75`). Switch handlers compose several snippets themselves (`TableSwitchHandler.java:11-43`; `LookupSwitchHandler.java:11-43`). Label, frame, and line nodes are metadata/control special cases rather than property-only opcodes (`LabelHandler.java:9-35`; `FrameHandler.java:17-116`; `LineNumberHandler.java:7-19`).

## Runtime/build templates

- The CMake template requires CMake 3.8, C++17, JNI, and a shared-library compiler; GNU gets `-O2 -s -DNDEBUG` (`sources/CMakeLists.txt:1-35`). It claims GCC >=6 and recognizes GNU, MSVC, Clang, and AppleClang (`sources/CMakeLists.txt:9-25`).
- The generated loader initializes JNI helpers at `JNI_OnLoad`, installs per-class registration callbacks, and registers `registerNativesForClass` (`sources/native_jvm_output.cpp:7-42`).
- The fixed runtime caches core classes/method IDs during initialization and implements class loading, exceptions, arrays, method-handle lookup, and local-reference cleanup (`sources/native_jvm.cpp:6-126,180-340`).
- Zig is an alternate post-generation compiler, not another code generator. It gathers all generated `.cpp`, invokes `zig c++ -std=c++17 -O2 -shared`, supplies a target `jni_md.h`, then injects/copies the library (`zig/ZigBuilder.java:44-70,76-118,121-176,180-209`). Target aliases/triples are in `zig/ZigTarget.java:18-101,140-190`.

## README alignment

The headline “Java .class to .cpp converter for use with JNI” matches this path (`README.md:1-2`). The warning that Java 9+ is experimental is justified (`README.md:4`): modern class metadata is not preserved compatibly after the forced major-version downgrade. The documented CMake and compiler prerequisites match generated CMake (`README.md:12-42,145-151`). Zig documentation matches the implemented direct C++17 build path (`README.md:155-196`), though no Zig tests exist in `obfuscator/src/test`.
