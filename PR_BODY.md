## Summary

- run real `--codegen=ir` compile/oracle/transpile/CMake/native/stdout E2E for
  every checked-in JDK 21 fixture;
- add three standard Java 21 fixtures for sequenced-set operations,
  sequenced-map view mutation, and virtual threads;
- narrowly split non-parameter temporary locals reused by both reference and
  int instructions in the IR-only method clone;
- document the environment, exact commands, admission accounting, pre-fix
  fallbacks, native exits, and byte-exact stdout results.

This does not change the default from `--codegen=legacy`, does not touch an
interpreter/evaluator or `--ir-lower`, and is not a JDK 21 support badge.

## Measured result

On the recorded Linux x86-64 host with OpenJDK/javac 21.0.10 and
`javac --release 21 -g`:

- 6/6 oracle runs exited 0;
- 47/47 code-bearing input methods used IR after exact inventory joining;
- 0 fallback, 0 unchanged constructor, and 0 missing input method;
- 6/6 GNU CMake configure and build stages exited 0;
- 6/6 transformed JARs exited 0;
- 6/6 transformed stdout files matched their oracle byte for byte.

Before the narrow fix, `RecordPatternsE2E` reproduced the two known
`ISTORE`/`ASTORE` local-carrier fallbacks (19/21 IR, 45/47 overall). Its hybrid
run still matched. After the fix it reached 21/21 IR and continued to match.

Full evidence: `docs/benchmarks/ir-jdk21-e2e-corpus.md`.

## Validation

```bash
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest.splitsReferenceAndIntReuseInTemporaryLocal \
  --tests by.radioegor146.ir.IrCompilerTest.rejectsIntStoreIntoInstanceReceiverLocal \
  --console=plain

./gradlew :obfuscator:shadowJar --console=plain --rerun-tasks
```

The six expanded compile, oracle, `--codegen=ir`, `CC=gcc CXX=g++` CMake,
packaging, transformed-run, and `cmp -s` commands are recorded in the report.

## (a) Change scope / 本次改动范围

**English:** Three JDK 21 fixtures, one focused IR frontend normalization and
unit test, and one behavioral measurement report. No default, interpreter,
evaluator, or production-goal change.

**中文：**新增三个 JDK 21 fixture、一个局部 IR frontend 规范化修复及单元测试，
并添加一份行为测量报告。不修改默认 codegen、interpreter、evaluator 或生产目标。

## (b) Can this ship to production as-is? / 是否可直接上线？

**English:** No. This is evidence from six programs on one JDK 21 Linux host,
not a Java 21 compatibility or production-support gate.

**中文：**不能。这只是一个 JDK 21 Linux 主机上六个程序的实测证据，不是 Java 21
兼容性或生产支持门禁。

## (c) Is review required? / 上线前是否需要 review？

**English:** Yes. Review should verify the IR-only clone/remap invariant,
admission inventory exclusions, and all native stdout comparisons.

**中文：**需要。Review 应核对仅作用于 IR clone 的 local remap 不变量、admission
inventory 排除规则，以及全部 native stdout 对比。

## (d) Review preconditions / Review 的前置条件

1. Confirm the branch is based on `origin/master` at `e997d71`.
2. Re-run the focused unit tests and all six commands in the report.
3. Require 47/47 exact input-method IR admission with no fallback or missing
   method.
4. Require all six CMake builds and transformed JARs to exit 0 and all six
   `cmp -s` checks to succeed.
5. Keep the default code generator `legacy` and do not interpret this result as
   a JDK 21 or JDK 25 support claim.
