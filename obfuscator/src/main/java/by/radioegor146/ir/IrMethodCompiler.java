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
        IrMethod method = frontend.build(context.clazz.name, context.method);
        MethodLoweringStrategy strategy = Objects.requireNonNull(
                loweringMode, "loweringMode") == IrLoweringMode.EVAL
                ? evaluatorStrategy : directStrategy;
        LoweredMethod lowered = strategy.lower(method, new LoweringContext(context));

        MethodShellEmitter.Shell shell;
        if (lowered.getShellKind() == LoweredMethod.ShellKind.EVALUATOR) {
            shell = shellEmitter.beginEvaluator(context);
            context.output.append(lowered.getBody());
            shellEmitter.finishEvaluator(context, shell);
        } else {
            shell = shellEmitter.beginIr(context);
            context.output.append(lowered.getBody());
            shellEmitter.finishIr(context, shell);
        }
    }
}
