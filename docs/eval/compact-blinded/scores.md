# Blinded readability scores

## Rubric

- **Full**: method signature, control flow, operations, constants, and return
  behavior match the fixture.
- **Partial**: the main intent is recovered but at least one material operation,
  constant, branch, or edge behavior is missing or wrong.
- **Fail**: the recovered behavior is materially different or absent.

The score covers the four generated static integer methods. Class-shell details
that do not appear as method behavior in the generated C++ are outside this
method-readability score.

## Ground-truth comparison

The fixture construction in
`InterpreterBackendIntegrationTest.writeFixtureJar` was opened only after both
recovery files had been committed. Its ASM instruction sequences exactly match
both written recoveries.

| Method | Opcode tree | Direct tree | Ground-truth result |
| --- | --- | --- | --- |
| `add(int,int)` | Full | Full | Exact add and return |
| `sumTo(int)` | Full | Full | Exact initialization, loop bound, accumulation, increment, and return |
| `mix(int,int)` | Full | Full | Exact constants, operation order, shifts, rotate, loop, and return |
| `divide(int,int)` | Full | Full | Exact integer division and Java edge behavior |
| **Total** | **4 Full / 0 Partial / 0 Fail** | **4 Full / 0 Partial / 0 Fail** | |

## H0 and interpretation

**H0:** at the coarse Full/Partial/Fail level, the compact opcode-backend tree
and direct C++ tree have equal method-level semantic recoverability for this
fixture.

The observed categorical scores do not reject H0: both trees scored four Full.
This does not establish equal readability. The direct tree exposed names,
comments, constants, labels, and operations in one per-method body. The opcode
tree required cross-referencing opaque byte arrays with the runtime decode
table, tracking little-endian operands and byte offsets, and reconstructing
control flow manually. H0 was not preregistered, and this single run has no
statistical power.

## Generated form and sufficiency

- The three lowered methods are **not** named-opcode arrays. They are generic
  `std::uint8_t` byte arrays; operation names exist only in the runtime source's
  decode table.
- `divide` remains a per-method `jvalue cstack`/`clocal` body in the opcode tree.
- The byte arrays plus `native_jvm_interp.cpp` were enough to recover all three
  lowered methods exactly.
- Those two evidence types were not enough to recover the entire four-method
  fixture because `divide` has no byte array; its fallback method body was also
  required.

## Contamination and limits

**Contamination: yes, limited and procedural.**

Before the opcode recovery, a commit summary and generated path listing exposed
the fixture class name. The first integration-test run exposed a test display
name stating that per-method fallback exists. The direct tree's file listing
and `CMakeLists.txt` were also opened during generation verification; they
showed only class/file names and the build source list, not method bodies,
constants, or control flow. No fixture instruction body, prior evaluation
document, prohibited architecture status, or recovery/score document from
another branch was read. The direct condition also necessarily followed the
opcode condition with the same reader, as required by the requested order.

This is **N=1** with one fixture and one reader. Because of the contamination,
sequential carryover, post-hoc H0, and lack of independent readers, this is
**not a scientific unaided-bar pass**. It is a reproducible compiler/readability
case study only.
