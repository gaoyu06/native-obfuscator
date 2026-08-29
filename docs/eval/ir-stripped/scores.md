# Recovery scores

N=1 reader. Scoring is strict source-structure recovery:

- **full**: source-level data/control behavior and meaningful operation sequence
  recovered;
- **partial**: observable behavior recovered, but meaningful source operations
  were erased or collapsed;
- **fail**: observable behavior was not recovered.

| Method | Score | Reveal comparison |
|---|---|---|
| `add(II)I` | full | Exact `a + b` reconstruction. |
| `sumTo(I)I` | full | Exact zero-initialized `i < n` accumulation of `0..n-1`. |
| `subMul(II)I` | full | Exact `(a - b) * b` reconstruction. |
| `mix(II)I` | partial | Exact observable `return 0` recovered, but the source's `IAND` → `IOR` → `IXOR` → `ISHL` → `ISHR` → `IUSHR` chain was algebraically erased by `-O2`. |
| `narrow(I)I` | partial | Exact observable `(char)(byte)(-value)` recovered; the source's redundant intermediate `I2S` was erased. |
| `bump([II)I` | full | Exact indexed increment, array-length return, and exception behavior recovered. |

Totals: **4 full, 2 partial, 0 fail**. Using full=1, partial=0.5,
fail=0 gives **5.0/6 (83.3%)**. A behavior-only score would be 6/6 because
all reconstructed methods are observationally equivalent to the fixture.

The interesting `mix` kernel was **not recovered as a source-level bitwise/shift
kernel** from the stripped shared object. Only its optimized constant-zero
behavior remained.

## Contamination

Before recovery, build setup exposed factory/method-family names through
compiled test metadata, and a documentation search exposed that `mix` exercised
bitwise and shift operations. No fixture source or oracle stdout was viewed
until after `recovery.md` existed. The disclosed hint did not reveal the exact
operation chain, and that chain is absent from the optimized machine code, so
`mix` remains partial rather than full.
