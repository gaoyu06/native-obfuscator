# Liveness gate

Status: candidate prepared; the post-publication runtime and disassembly gate
will be recorded against the committed `published.jar` / `published.so` bytes.

## Required checks

- Java-oracle stdout and native-jar stdout compare byte-for-byte.
- Six `mix` cases produce at least four distinct results and are not all zero.
- The native `mix` function remains present and is a trampoline into
  `evaluate_i32`, not a straight-line implementation of the Java expression.
- The serialized method data contains live integer multiply, add, subtract,
  branch, and return opcodes, and the evaluator dispatcher handling them remains
  present in the stripped object.

The fixture compiler run already established that generated `mix(II)I` contains
`ir_method_data` plus `native_jvm::ir_eval::evaluate_i32` and that no legacy
fallback was logged. Final evidence will use the published bytes rather than
relying only on generated source.
