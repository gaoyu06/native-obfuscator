package by.radioegor146.ir.frontend;

import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Expands legacy {@code jsr}/{@code ret} subroutines on a private method copy.
 *
 * <p>The source method is never handed to ASM's inliner, so malformed legacy
 * control flow remains a fail-closed IR capability miss without partial
 * bytecode mutation.
 */
public final class JsrRetInliner {
    private JsrRetInliner() {
    }

    public static MethodNode inline(MethodNode method) {
        if (!containsJsrOrRet(method)) {
            return method;
        }

        MethodNode inlined = new MethodNode(Opcodes.ASM9, method.access,
                method.name, method.desc, method.signature,
                method.exceptions == null
                        ? null : method.exceptions.toArray(new String[0]));
        try {
            JSRInlinerAdapter adapter = new JSRInlinerAdapter(
                    inlined, method.access, method.name, method.desc,
                    method.signature,
                    method.exceptions == null
                            ? null : method.exceptions.toArray(new String[0]));
            method.accept(adapter);
        } catch (RuntimeException malformedSubroutine) {
            throw unsupported(malformedSubroutine);
        }

        if (containsJsrOrRet(inlined)) {
            throw new UnsupportedIrConstructException(
                    "Malformed JSR/RET subroutine cannot be inlined: "
                            + "inliner left legacy subroutine instructions");
        }
        return inlined;
    }

    private static boolean containsJsrOrRet(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.JSR
                    || instruction.getOpcode() == Opcodes.RET) {
                return true;
            }
        }
        return false;
    }

    private static UnsupportedIrConstructException unsupported(
            RuntimeException failure) {
        String detail = failure.getMessage();
        return new UnsupportedIrConstructException(
                "Malformed JSR/RET subroutine cannot be inlined"
                        + (detail == null || detail.isEmpty()
                        ? "" : ": " + detail));
    }
}
