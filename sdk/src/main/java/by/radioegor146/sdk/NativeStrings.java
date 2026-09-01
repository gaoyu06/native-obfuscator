/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package by.radioegor146.sdk;

/**
 * @deprecated Use {@link by.radioegor146.nativeobfuscator.NativeStrings}.
 * This class remains in generated JARs so existing callers keep compiling.
 */
@Deprecated
public final class NativeStrings {

    private NativeStrings() {
    }

    public static int length(String value) {
        return by.radioegor146.nativeobfuscator.NativeStrings.length(value);
    }

    public static int hashCode(String value) {
        return by.radioegor146.nativeobfuscator.NativeStrings.hashCode(value);
    }

    public static String concat(String left, String right) {
        return by.radioegor146.nativeobfuscator.NativeStrings.concat(left, right);
    }
}
