package by.radioegor146.ir.backend;

import java.util.Arrays;
import java.util.Objects;

/**
 * Result of lowering one IR method. The shell kind tells the compiler whether
 * the body needs the regular IR JNI prologue or is already a complete thin
 * evaluator trampoline.
 */
public final class LoweredMethod {
    public enum ShellKind {
        DIRECT,
        EVALUATOR
    }

    private final ShellKind shellKind;
    private final String body;
    private final byte[] methodData;

    private LoweredMethod(ShellKind shellKind, String body, byte[] methodData) {
        this.shellKind = Objects.requireNonNull(shellKind, "shellKind");
        this.body = Objects.requireNonNull(body, "body");
        this.methodData = methodData == null ? null : methodData.clone();
    }

    public static LoweredMethod direct(String body) {
        return new LoweredMethod(ShellKind.DIRECT, body, null);
    }

    public static LoweredMethod evaluator(String body, byte[] methodData) {
        return new LoweredMethod(ShellKind.EVALUATOR, body,
                Objects.requireNonNull(methodData, "methodData"));
    }

    public ShellKind getShellKind() {
        return shellKind;
    }

    public String getBody() {
        return body;
    }

    public byte[] getMethodData() {
        return methodData == null ? null : Arrays.copyOf(methodData, methodData.length);
    }
}
