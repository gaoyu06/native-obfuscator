# test: admit extra-local int as second, third, fourth, and fifth five-arg NEW arguments

## English

This fixture-only change admits the shape
`new-constructor-extra-local-argument-five-second-third-fourth-fifth`.

- Helper wiring: the first initializer argument is `ICONST_1`; the second,
  third, fourth, and fifth are `ILOAD 3`.
- Initializer argument count: 5.
- Constructor-chain descriptor:
  `(Ljava/util/GregorianCalendar;)V`.
- `ConstructorSpecialMethodProcessor` changed: No.
- Product defaults changed: No (`--codegen`, `--ir-lower`, and `--backend`
  remain unchanged).
- Rebased onto leftover-docs #414 (`2cd5fb00`). Latest compiler parent XML remains **#413 (710)** until the parent re-run.
- Expected parent XML after leftover-docs: 713 tests (710 + 3).
- Ship-ready: No.
- This is fixture admission coverage, not a JDK support badge.

## 中文

此变更仅修改测试夹具，准入形状
`new-constructor-extra-local-argument-five-second-third-fourth-fifth`。

- 辅助方法接线：初始化器第一个参数为 `ICONST_1`，第二、第三、第四和第五个
  参数均为 `ILOAD 3`。
- 初始化器参数数量：5。
- 构造器链描述符：`(Ljava/util/GregorianCalendar;)V`。
- 是否修改 `ConstructorSpecialMethodProcessor`：否。
- 是否修改产品默认值：否（`--codegen`、`--ir-lower` 和 `--backend`
  均保持不变）。
- 已 rebase 到 leftover-docs #414（`2cd5fb00`）。父级重跑前最新编译器父级 XML 仍为 **#413（710）**。
- leftover-docs 之后预期父分支 XML：713 个测试（710 + 3）。
- 可发布：否。
- 此项仅为测试夹具准入覆盖，不代表 JDK 支持徽章。
