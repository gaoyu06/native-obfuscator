# docs: remeasure IR leftovers after leftover-docs #397

## English

### Scope

- Measured tree and measurement SHA: leftover-docs [#397](https://github.com/gaoyu06/native-obfuscator/pull/397), `9675616f6d2e84442d7465232c2705dfd152c11d`.
- Merge-base with `origin/master` at measurement: `9675616f6d2e84442d7465232c2705dfd152c11d`.
- This remeasurement supersedes [#397](https://github.com/gaoyu06/native-obfuscator/pull/397)'s measurement of leftover-docs [#395](https://github.com/gaoyu06/native-obfuscator/pull/395) at `38c8cf0` (`38c8cf02920609f12a2717f799c8a0b228876b65`).
- Latest compiler parent XML: **[#396](https://github.com/gaoyu06/native-obfuscator/pull/396) (686)** (`IrCompilerTest` 679 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**.
- Admitted: **No**.
- Ship-ready: **No**.
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Measured with:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 范围

- 测量树及测量 SHA：leftover-docs [#397](https://github.com/gaoyu06/native-obfuscator/pull/397)，`9675616f6d2e84442d7465232c2705dfd152c11d`。
- 测量时与 `origin/master` 的 merge-base：`9675616f6d2e84442d7465232c2705dfd152c11d`。
- 本次重新测量取代 [#397](https://github.com/gaoyu06/native-obfuscator/pull/397) 对 leftover-docs [#395](https://github.com/gaoyu06/native-obfuscator/pull/395) `38c8cf0`（`38c8cf02920609f12a2717f799c8a0b228876b65`）的测量。
- 最新编译器父级 XML：**[#396](https://github.com/gaoyu06/native-obfuscator/pull/396)（686）**（`IrCompilerTest` 679 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
- Processor changed：**No**。
- Admitted：**No**。
- Ship-ready：**No**。
- 测得零 leftover **不表示覆盖完整**，**不是 JDK 支持标志**，也**不授权切换默认值**。
- 默认值保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### Joined corpus 总计

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |
