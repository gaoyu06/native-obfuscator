# test: admit extra-local int as the second six-arg NEW argument

## English

This fixture-only change admits the shape
`new-constructor-extra-local-argument-six-second`.

- Helper wiring: first initializer argument `ICONST_1`; second `ILOAD 3`;
  third/fourth/fifth `ICONST_3` / `ICONST_4` / `ICONST_5`; sixth
  `BIPUSH 6`.
- Constructor argument count: 6.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.
- Processor changed: No.
- Ship-ready: No.
- Expected parent XML after leftover-docs: 716 tests (713 + 3).
- The `--codegen`, `--ir-lower`, and `--backend` defaults are unchanged.
- This is fixture admission coverage, not a JDK support badge.

## 中文

本次仅修改测试夹具，新增接纳形状
`new-constructor-extra-local-argument-six-second`。

- 辅助方法接线：初始化器第一个参数为 `ICONST_1`，第二个参数为
  `ILOAD 3`，第三/第四/第五个参数分别为 `ICONST_3` / `ICONST_4` /
  `ICONST_5`，第六个参数为 `BIPUSH 6`。
- 构造器参数数量：6。
- 链式调用描述符：`(Ljava/util/GregorianCalendar;)V`。
- 是否修改处理器：否。
- 是否可发布：否。
- leftover-docs 之后预期父分支 XML：716 个测试（713 + 3）。
- `--codegen`、`--ir-lower` 和 `--backend` 的默认值均未改变。
- 这是测试夹具接纳覆盖，不代表 JDK 支持徽章。
