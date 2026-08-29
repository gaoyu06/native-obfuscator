## Summary

This widens the explicit, default-off in-process interpreter from ISA v3 to
ISA v4 with its first reference/object slice.

- Preserve opcode values 1–45 and append `ACONST_NULL=46`, `ALOAD=47`,
  `ASTORE=48`, `ARETURN=49`, `IFNULL=50`, and `IFNONNULL=51`.
- Admit static object/array descriptors when every instruction is supported.
- Keep references as opaque `jobject` values in parallel one-slot local and
  operand-stack arrays; long values remain two-slot.
- Transfer JNI reference arguments at descriptor slot offsets and return
  references through `execute_l`.
- Continue rejecting unsupported operations before interpreter mutation and
  fall back per method to the selected code generator.
- Keep `--backend=cpp` and `--codegen=legacy` as the defaults.

Object creation, field access, method calls, special calls, and exception
dispatch are outside this increment. The dispatcher makes no JNI call.

| Value | Opcode | Encoding |
|---:|---|---|
| 46 | `ACONST_NULL` | no operand |
| 47 | `ALOAD` | `u16 local` |
| 48 | `ASTORE` | `u16 local` |
| 49 | `ARETURN` | no operand |
| 50 | `IFNULL` | `i32 absolute target` |
| 51 | `IFNONNULL` | `i32 absolute target` |

## Verification

Verification results and exact test counts will be recorded after running the
required suite and both complete generated-tree comparisons.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Add null constants, one-slot reference locals and
  operand-stack values, null branches, reference return, JNI argument
  transfer, and an `execute_l` dispatcher entry to ISA v4. /
  在 ISA v4 中加入空引用常量、单槽引用局部变量与操作数栈值、空引用分支、
  引用返回、JNI 参数传递和 `execute_l` 调度入口。
- **(b) Ship-ready and review / 可直接发布及审查:** **Ship-ready: No /
  可直接发布：否。 Review required: Yes / 需要审查：是。** Sol-only review
  is acceptable. The optional backend remains incomplete. /
  可由 Sol 单独审查。该可选后端仍未完整实现。
- **(c) Compatibility / 兼容性:** Opcode values 1–45 remain unchanged, ISA
  versions are checked exactly, unsupported methods retain per-method
  fallback, and default generation remains C++/legacy. /
  操作码 1–45 保持不变，ISA 版本继续严格检查，不支持的方法保留逐方法回退，
  默认生成方式仍为 C++/legacy。
- **(d) Evidence / 证据:** Exact required-suite counts and detached-master
  complete-tree comparison results will be inserted after verification. /
  验证完成后将填写必需测试套件的准确数量及 detached master 的完整目录比较
  结果。
