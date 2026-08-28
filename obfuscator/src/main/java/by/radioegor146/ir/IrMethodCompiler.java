package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;

/**
 * Orchestrates ASM frontend, direct structured C++ emission, and the shared JNI
 * shell. Frontend/emitter work completes before the shell mutates bytecode, so
 * capability misses remain safe to fall back per method.
 */
public final class IrMethodCompiler {
    private final AsmToIr frontend;
    private final IrCppEmitter emitter;
    private final MethodShellEmitter shellEmitter;

    public IrMethodCompiler(MethodShellEmitter shellEmitter) {
        this(new AsmToIr(), new IrCppEmitter(), shellEmitter);
    }

    IrMethodCompiler(AsmToIr frontend, IrCppEmitter emitter,
                     MethodShellEmitter shellEmitter) {
        this.frontend = frontend;
        this.emitter = emitter;
        this.shellEmitter = shellEmitter;
    }

    public void processMethod(MethodContext context) {
        IrMethod method = frontend.build(context.clazz.name, context.method);
        String body = emitter.emitBody(method);

        MethodShellEmitter.Shell shell = shellEmitter.beginIr(context);
        context.output.append(body);
        shellEmitter.finishIr(context, shell);
    }
}
