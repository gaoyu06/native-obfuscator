# English

(a) Change

- Admit proven `ConstantDynamic` LDCs in eligible interfaces on the direct IR/C++ path.
- Keep the synchronized resolver and all mutable cache fields on a deterministic `HiddenMethodsPool` companion under `nativeN/hidden/`.
- Keep only an uncached bootstrap bridge on the interface so `MethodHandles.lookup()` retains the interface lookup class and private bootstrap access.

(b) Safety boundary

- Preserve the existing static-bootstrap, exact `Lookup`/`String`/`Class` prefix, exact static-argument, matching-return, malformed-input, and cycle checks.
- Validate companion placement and resolver/bridge collisions before mutating the caller or hidden pool.
- Continue to reject non-static, variable-arity, malformed, cyclic, or otherwise unproven condy shapes.
- Load application-dependent companions through the transformed application's class loader; existing generic hidden helpers retain their current path.

(c) Verification

- Required focused command passed:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
- JUnit XML: `IrCompilerTest` 145 tests, 0 skipped, 0 failures, 0 errors; `CodegenModeTest` 7 tests, 0 skipped, 0 failures, 0 errors.
- The interface parity test configures and builds with CMake/g++, then runs HotSpot with `-Xverify:all -Xcheck:jni`; string and int outputs match the reference run and duplicate LDCs resolve once.

(d) Release status

- Ship-ready: **No**
- Stacked review: **No**
- Default flip: **No** (`--codegen=legacy`, direct IR lowering, and the C++ backend defaults are unchanged.)

# 中文

(a) 变更

- 在 direct IR/C++ 路径中接纳满足既有证明条件的接口内 `ConstantDynamic` LDC。
- 同步解析器及全部可变缓存字段位于 `nativeN/hidden/` 下由 `HiddenMethodsPool` 管理的确定性伴生类中。
- 接口仅增加无缓存的 bootstrap 桥接方法，使 `MethodHandles.lookup()` 仍以接口为 lookup class，并保留访问私有 bootstrap 的能力。

(b) 安全边界

- 保留既有检查：静态 bootstrap、精确的 `Lookup`/`String`/`Class` 参数前缀、静态参数逐项精确匹配、返回类型匹配、格式校验和循环检测。
- 在修改调用方或 hidden pool 前完成伴生类放置及解析器/桥接成员冲突校验。
- 非静态、可变参数、格式错误、循环或其他未经证明的 condy 形态继续拒绝。
- 依赖应用接口的伴生类由转换后应用的类加载器加载；既有通用 hidden helper 的加载路径不变。

(c) 验证

- 指定 focused 命令通过：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
- JUnit XML：`IrCompilerTest` 145 项，0 skipped，0 failures，0 errors；`CodegenModeTest` 7 项，0 skipped，0 failures，0 errors。
- 接口 parity 测试使用 CMake/g++ 配置和构建，并以 `-Xverify:all -Xcheck:jni` 运行 HotSpot；字符串和整数输出与参考运行一致，重复 LDC 仅解析一次。

(d) 发布状态

- Ship-ready：**No**
- Stacked review：**No**
- Default flip：**No**（`--codegen=legacy`、direct IR lowering 和 C++ backend 的默认值均未改变。）
