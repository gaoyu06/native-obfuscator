package by.radioegor146.ir;

import by.radioegor146.IrLoweringMode;
import by.radioegor146.MethodContext;
import by.radioegor146.ir.backend.DirectCppStrategy;
import by.radioegor146.ir.backend.InterpreterStreamStrategy;
import by.radioegor146.ir.backend.LoweredMethod;
import by.radioegor146.ir.backend.LoweringContext;
import by.radioegor146.ir.backend.MethodLoweringStrategy;
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import by.radioegor146.ir.frontend.DynamicConstantSupport;
import by.radioegor146.ir.frontend.JsrRetInliner;
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.Objects;

/**
 * Orchestrates the ASM frontend, selected IR lowering, and shared JNI shell.
 * Frontend/lowering work completes before the shell mutates bytecode, so
 * capability misses remain safe to fall back per method.
 */
public final class IrMethodCompiler {
    private final AsmToIr frontend;
    private final MethodLoweringStrategy directStrategy;
    private final MethodLoweringStrategy evaluatorStrategy;
    private final MethodShellEmitter shellEmitter;

    public IrMethodCompiler(MethodShellEmitter shellEmitter) {
        this(new AsmToIr(), new DirectCppStrategy(new IrCppEmitter()),
                new InterpreterStreamStrategy(), shellEmitter);
    }

    IrMethodCompiler(AsmToIr frontend, MethodLoweringStrategy directStrategy,
                     MethodLoweringStrategy evaluatorStrategy,
                     MethodShellEmitter shellEmitter) {
        this.frontend = Objects.requireNonNull(frontend, "frontend");
        this.directStrategy = Objects.requireNonNull(directStrategy, "directStrategy");
        this.evaluatorStrategy = Objects.requireNonNull(
                evaluatorStrategy, "evaluatorStrategy");
        this.shellEmitter = Objects.requireNonNull(shellEmitter, "shellEmitter");
    }

    public void processMethod(MethodContext context) {
        processMethod(context, IrLoweringMode.DIRECT);
    }

    public void processMethod(MethodContext context, IrLoweringMode loweringMode) {
        MethodNode workingMethod = JsrRetInliner.inline(context.method);
        MethodNode bytecodeBody = workingMethod;
        if ("<init>".equals(context.method.name)) {
            if (workingMethod != context.method) {
                normalizeSimplePrefixSubroutines(context, workingMethod);
            }
            // Validate the split before the general frontend so path-sensitive
            // prefix-local diagnostics are reported before any C++ or bridge
            // state is created. The emitted helper starts immediately after the
            // mandatory this/super call retained in bytecode.
            bytecodeBody = ConstructorSpecialMethodProcessor.createNativeBody(
                    context.clazz, workingMethod);
            frontend.build(context.clazz.name, workingMethod);
        }
        String dynamicConstantResolverOwner =
                DynamicConstantSupport.validateResolverInstallation(
                        context.clazz, bytecodeBody,
                        context.obfuscator.getHiddenMethodsPool());
        IrMethod method = frontend.build(
                context.clazz.name, dynamicConstantResolverOwner, bytecodeBody);
        IrLoweringMode selectedMode = Objects.requireNonNull(loweringMode, "loweringMode");
        if (selectedMode == IrLoweringMode.EVAL && method.isSynchronizedMethod()) {
            throw new UnsupportedIrConstructException(
                    "Evaluator lowering does not support synchronized methods");
        }
        MethodLoweringStrategy strategy = selectedMode == IrLoweringMode.EVAL
                ? evaluatorStrategy : directStrategy;
        LoweredMethod lowered = strategy.lower(method, new LoweringContext(context));
        DynamicConstantSupport.installResolvers(
                context.clazz, bytecodeBody,
                context.obfuscator.getHiddenMethodsPool());

        if ("<init>".equals(context.method.name)
                && workingMethod != context.method) {
            installInlinedCode(context.method, workingMethod);
        }
        if (method.isSynchronizedMethod()) {
            // The IR body owns the method monitor explicitly. Clear the JVM
            // access flag only after frontend and lowering validation succeed.
            context.method.access &= ~Opcodes.ACC_SYNCHRONIZED;
        }
        MethodShellEmitter.Shell shell = shellEmitter.beginIr(context);
        context.output.append(lowered.getBody());
        shellEmitter.finishIr(context, shell);
    }

    private static void installInlinedCode(MethodNode target, MethodNode source) {
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
            MethodContext context, MethodNode method) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return;
        }
        boolean changed;
        do {
            changed = false;
            int chainCallIndex = directChainCallIndex(context, method);
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
            MethodContext context, MethodNode method) {
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("<init>".equals(call.name)
                    && (context.clazz.name.equals(call.owner)
                    || Objects.equals(context.clazz.superName, call.owner))) {
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
}
