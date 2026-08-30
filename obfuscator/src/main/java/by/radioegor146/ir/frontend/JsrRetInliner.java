package by.radioegor146.ir.frontend;

import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.Objects;

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

    public static MethodNode prepareForIr(ClassNode owner, MethodNode method) {
        MethodNode inlined = inline(method);
        if (inlined != method && "<init>".equals(method.name)) {
            normalizeSimplePrefixSubroutines(owner, inlined);
        }
        return inlined;
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

    public static void installCode(MethodNode target, MethodNode source) {
        target.instructions = source.instructions;
        target.tryCatchBlocks = source.tryCatchBlocks;
        target.localVariables = source.localVariables;
        target.visibleLocalVariableAnnotations =
                source.visibleLocalVariableAnnotations;
        target.invisibleLocalVariableAnnotations =
                source.invisibleLocalVariableAnnotations;
        target.maxStack = source.maxStack;
        target.maxLocals = source.maxLocals;
    }

    /**
     * ASM emits each inlined subroutine clone after the main instruction
     * stream. For a straight-line prefix subroutine this creates a lexical jump
     * across the constructor-chain call even though the runtime path returns
     * before that call. Move only that exact generated block next to its call
     * site. More complex prefix subroutines remain fail-closed in the existing
     * constructor control-flow proof.
     */
    private static void normalizeSimplePrefixSubroutines(
            ClassNode owner, MethodNode method) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return;
        }
        boolean changed;
        do {
            changed = false;
            int chainCallIndex = directChainCallIndex(owner, method);
            if (chainCallIndex < 0) {
                return;
            }
            for (int i = 0; i < chainCallIndex; i++) {
                AbstractInsnNode instruction = method.instructions.get(i);
                if (!(instruction instanceof JumpInsnNode)
                        || instruction.getOpcode() != Opcodes.GOTO) {
                    continue;
                }
                JumpInsnNode call = (JumpInsnNode) instruction;
                if (method.instructions.indexOf(call.label) < chainCallIndex) {
                    continue;
                }
                LabelNode continuation = nextLabel(call);
                AbstractInsnNode end = straightLineSubroutineEnd(
                        method, call.label, continuation);
                if (continuation == null || end == null) {
                    continue;
                }
                InsnList moved = new InsnList();
                AbstractInsnNode current = call.label;
                while (current != null) {
                    AbstractInsnNode next = current.getNext();
                    method.instructions.remove(current);
                    moved.add(current);
                    if (current == end) {
                        break;
                    }
                    current = next;
                }
                method.instructions.insert(call, moved);
                changed = true;
                break;
            }
        } while (changed);
    }

    private static int directChainCallIndex(
            ClassNode owner, MethodNode method) {
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("<init>".equals(call.name)
                    && (owner.name.equals(call.owner)
                    || Objects.equals(owner.superName, call.owner))) {
                return i;
            }
        }
        return -1;
    }

    private static LabelNode nextLabel(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getNext();
             current != null; current = current.getNext()) {
            if (current instanceof LabelNode) {
                return (LabelNode) current;
            }
            if (current.getOpcode() >= 0) {
                return null;
            }
        }
        return null;
    }

    private static AbstractInsnNode straightLineSubroutineEnd(
            MethodNode method, LabelNode start, LabelNode continuation) {
        if (continuation == null
                || method.instructions.indexOf(start)
                <= method.instructions.indexOf(continuation)) {
            return null;
        }
        for (AbstractInsnNode current = start;
             current != null; current = current.getNext()) {
            if (current instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) current;
                return jump.getOpcode() == Opcodes.GOTO
                        && jump.label == continuation ? jump : null;
            }
            if (current instanceof TableSwitchInsnNode
                    || current instanceof LookupSwitchInsnNode
                    || current.getOpcode() == Opcodes.ATHROW
                    || current.getOpcode() >= Opcodes.IRETURN
                    && current.getOpcode() <= Opcodes.RETURN) {
                return null;
            }
        }
        return null;
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
