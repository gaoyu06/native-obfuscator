## Summary

- admit proven prefix extra-local long copies as long-shift values
- admit those copies as `LDIV` and `LREM` operands
- retain long arithmetic in the JVM constructor prefix with one hidden bridge
- keep extra-local long shift counts, unsafe `LNEG`, and nine-or-more nested long binaries rejected

Ship-ready: **No**

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

## 中文说明

- 允许已证明的构造器前缀额外 long 局部变量副本作为 long 移位的 value
- 允许该副本作为 `LDIV` 和 `LREM` 操作数
- long 运算仍保留在 JVM 构造器前缀中，并且只生成一个隐藏 bridge
- 额外 long 局部变量作为移位 count、不安全的 `LNEG`、以及九层及以上 long 链仍保持拒绝

可发布状态：**否**
