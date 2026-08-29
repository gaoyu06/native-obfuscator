# (a) Change / 变更

EN: Admit 2–8 pairwise-distinct nonempty linear constructor suffixes that read
proven prefix extra locals. The CFG proof requires each forwarded local to be
definitely assigned with one compatible carrier on every path that invokes the
hidden bridge. Packed extras precede the trailing path-id `int` in the
independent descriptor and wrapper loads.

中文：支持 2–8 个两两不同、非空且线性的构造函数字节码后缀读取已证明的前缀额外局部变量。CFG
证明要求每个转发局部变量在所有调用隐藏桥接方法的路径上均已明确赋值，且载体类型唯一兼容。独立方法描述符和包装器加载中，压缩后的额外局部变量位于末尾路径编号 `int` 之前。

# (b) Ship-ready? / 可直接上线？

EN: No.

中文：否。

# (c) Verification / 验证

EN: Added admission, JVM verification, CMake/g++ JNI parity, and reject-before-
mutation coverage. The required focused Gradle gate will be recorded after it
is run.

中文：新增了准入、JVM 验证、CMake/g++ JNI 一致性以及变更前拒绝测试。运行指定的
Gradle 聚焦测试门禁后，将在此记录结果。

# (d) Scope and invariants / 范围与约束

EN: Existing branch, exception-table, hybrid suffix, nested/`IDIV` input,
mixed-catch, and `jsr`/`ret` rejects remain unchanged. Identical one-join copies
and immediate-return multi-super constructors remain on their existing paths.
The `--codegen`, `--ir-lower`, and `--backend` defaults are unchanged.

中文：现有的分支、异常表、混合后缀、嵌套/`IDIV` 输入、混合 catch 以及
`jsr`/`ret` 拒绝规则保持不变。相同的单汇合点副本和立即返回的多 super
构造函数继续使用原有路径。`--codegen`、`--ir-lower` 和 `--backend`
默认值均未更改。
