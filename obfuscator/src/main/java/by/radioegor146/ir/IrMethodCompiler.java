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
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

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
        MethodNode bytecodeBody = context.method;
        if ("<init>".equals(context.method.name)) {
            // Validate the split before the general frontend so path-sensitive
            // prefix-local diagnostics are reported before any C++ or bridge
            // state is created. The emitted helper starts immediately after the
            // mandatory this/super call retained in bytecode.
            bytecodeBody = ConstructorSpecialMethodProcessor.createNativeBody(
                    context.clazz, context.method);
            frontend.build(context.clazz.name, context.method);
        }
        DynamicConstantSupport.validateResolverInstallation(
                context.clazz, bytecodeBody);
        IrMethod method = frontend.build(context.clazz.name, bytecodeBody);
        IrLoweringMode selectedMode = Objects.requireNonNull(loweringMode, "loweringMode");
        if (selectedMode == IrLoweringMode.EVAL && method.isSynchronizedMethod()) {
            throw new UnsupportedIrConstructException(
                    "Evaluator lowering does not support synchronized methods");
        }
        MethodLoweringStrategy strategy = selectedMode == IrLoweringMode.EVAL
                ? evaluatorStrategy : directStrategy;
        LoweredMethod lowered = strategy.lower(method, new LoweringContext(context));
        DynamicConstantSupport.installResolvers(context.clazz, bytecodeBody);

        if (method.isSynchronizedMethod()) {
            // The IR body owns the method monitor explicitly. Clear the JVM
            // access flag only after frontend and lowering validation succeed.
            context.method.access &= ~Opcodes.ACC_SYNCHRONIZED;
        }
        MethodShellEmitter.Shell shell = shellEmitter.beginIr(context);
        context.output.append(lowered.getBody());
        shellEmitter.finishIr(context, shell);
    }
}
