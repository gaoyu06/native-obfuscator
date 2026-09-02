package by.radioegor146;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replace same-class {@code invokestatic} of another IR-transpiled method
 * with a direct C++ call instead of {@code CallStatic*Method}.
 *
 * <p>{@link #OFF} is the default. The call still uses the generated JNI
 * function, so Java callers are unchanged. Direct calls omit the extra
 * Java native frame.</p>
 */
public enum DirectNativeCallMode {
    OFF,
    ON;

    public boolean enabled() {
        return this == ON;
    }

    public static boolean calleeEligible(ClassNode classNode, MethodNode method) {
        if (classNode == null || method == null) {
            return false;
        }
        if (Util.getFlag(classNode.access, Opcodes.ACC_INTERFACE)) {
            return false;
        }
        if (!Util.getFlag(method.access, Opcodes.ACC_STATIC)) {
            return false;
        }
        if (Util.getFlag(method.access, Opcodes.ACC_SYNCHRONIZED)) {
            return false;
        }
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
            return false;
        }
        return true;
    }
}
