# IR shared-evaluator recovery scores

## Protocol

- Subjects: **N=1** (`published.so`).
- Recovery was written and committed first as `8bab3fb`.
- No source or oracle material was viewed before that commit.
- Only after the recovery commit, `published.jar` and `run.md` were opened for
  scoring.
- Scale: **full** = formula/control flow recovered correctly; **partial** =
  material behavior recovered but incomplete or inaccurate; **none** = no
  useful algorithm recovered.

## Per-method scores

| Method | Score | Oracle comparison |
|---|---|---|
| `add(int, int)` | **full** | Recovered `x + y`; published input `(17, 25)` produces `42`. |
| `sumTo(int)` | **full** | Recovered the loop summing `0 .. n-1`; published input `10` produces `45`. |
| `subMul(int, int, int)` | **full** | Recovered `(x - y) * z`; published input `(20, 7, 3)` produces `39`. |
| `mix(int, int)` | **full** | Recovered both signed branches and all arithmetic; all six published input/output pairs match exactly. |

For `mix`, the post-recovery oracle pairs were:

```text
mix(0,0)=-385
mix(1,2)=2028
mix(2,1)=1500
mix(-3,5)=-593
mix(7,-4)=3060
mix(123,456)=409744
```

These exercise both sides of `x <= y` and both signs of the wrapped
intermediate `q`; each agrees with the recovered control flow.

## Subject validity

**Valid live evaluator subject: yes.**  The stripped ELF contains four
per-method trampolines that pass distinct live `NJE` blobs to the shared
`native_jvm::ir_eval::evaluate_i32` implementation.  The blobs retain
arithmetic, comparison, branch, jump, copy, and return instructions.  This is
not dead-code elimination and not a set of direct, constant-folded native
method bodies.  `run.md` independently records that the native jar matched the
Java oracle and that `mix` had no legacy or direct-IR fallback.

## Result

- Full: **4**
- Partial: **0**
- None: **0**
- Method-level full-recovery rate: **4/4**
