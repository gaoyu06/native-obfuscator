package by.radioegor146.nativeobfuscator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method for native obfuscation. Optional attributes override
 * the matching CLI flags for that class or method; method values win over
 * class values, and {@code INHERIT} keeps the CLI default.
 *
 * <p>{@code -a} still controls which methods are selected. These attributes
 * apply whenever a selected method or its class carries {@code @Native}.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Native {
    NativeLowering lowering() default NativeLowering.INHERIT;

    NativeIntrinsics intrinsics() default NativeIntrinsics.INHERIT;

    NativeBackend backend() default NativeBackend.INHERIT;

    NativeCfObfuscation cfObfuscation() default NativeCfObfuscation.INHERIT;
}
