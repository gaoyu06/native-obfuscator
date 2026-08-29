## Summary

Records the 2026-08-29 maintainer goal reset. The active engineering goal is now: migrate every method-body native codegen path onto typed CFG IR (no new string-concat / snippet work), then keep going until the legacy path can be deleted.

Docs only. No compiler, CLI default, or measurement changes.

## (a) 本次改动范围 / Change scope

- Add `docs/architecture/current-goal.md`
- Point README, docs index, project-status, D7, and the historical migration plan at that page
- 未改编译器、默认值或测试数字

## (b) 是否可直接上线 / Can this ship to production as-is?

**No / 否.** This is a goal statement. Legacy is still the default and still required.

## (c) 上线前是否需要 review / Is review required?

**Yes / 是.** Confirm the page does not claim legacy is already gone and does not flip `--codegen`.

## (d) review 的前置条件 / Review preconditions

- Default remains `legacy` in every CLI/doc table
- Known IR leftovers are listed as incomplete, not as a full JVM matrix
- Interpreter / evaluator stay side paths

## 中文摘要

现行目标改为：方法体全部迁到 IR，停止字符串拼接，直到可以完整废弃 legacy。本文只改文档。默认值未变。目标尚未完成。
