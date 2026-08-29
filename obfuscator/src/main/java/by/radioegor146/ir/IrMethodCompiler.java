package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import org.objectweb.asm.tree.MethodNode;

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
        MethodNode bytecodeBody = context.method;
        if ("<init>".equals(context.method.name)) {
            // Validate the complete constructor before creating either C++ state
            // or the verifier-safe bridge. The emitted helper starts immediately
            // after the mandatory this/super call retained in bytecode.
            frontend.build(context.clazz.name, context.method);
            bytecodeBody = ConstructorSpecialMethodProcessor.createNativeBody(
                    context.clazz, context.method);
        }
        IrMethod method = frontend.build(context.clazz.name, bytecodeBody);
        String body = emitter.emitBody(method, context);

        MethodShellEmitter.Shell shell = shellEmitter.beginIr(context);
        context.output.append(body);
        shellEmitter.finishIr(context, shell);
    }
}
