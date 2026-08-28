# Blinded recovery scores

The oracle was opened only after both recovery documents had been committed.
The fixture oracle is the bytecode construction in
`InterpreterBackendIntegrationTest.writeFixtureJar`; there is no
`DemoKernel.java` file on this branch.

`full` means the recovery has the method's exact operation sequence, constants,
loop condition, and return behavior. `partial` means a material detail is
missing or wrong. `fail` means the behavior was not recovered.

| Method | Opcode artifact | Direct artifact | Oracle comparison |
| --- | --- | --- | --- |
| `add(int,int)` | full | full | Both recovered integer addition exactly. |
| `sumTo(int)` | full | full | Both recovered zero initialization, `i < n`, accumulation of `i`, increment, and return exactly. |
| `mix(int,int)` | full | full | Both recovered both constants, unsigned right shift, operation order, rotation distance, loop bound, and return exactly. |
| `divide(int,int)` | full | full | Both recovered integer division exactly; the opcode artifact exposed this method through its direct per-method fallback. |

Totals: opcode artifact **4 full / 0 partial / 0 fail**; direct artifact
**4 full / 0 partial / 0 fail**.

The observed categorical recovery scores are equal, so H0 (equal recovery) is
not rejected. The direct artifact required less decoding effort, but that
readability observation does not change the requested full/partial/fail result.

This is one generated class (`N=1`). It is not a scientific unaided-bar pass:
the reading was tool-assisted, and the run also has the protocol contamination
recorded in `run.md`.
