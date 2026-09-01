package by.radioegor146.ir;

import by.radioegor146.ControlFlowObfuscationMode;
import by.radioegor146.IrLoweringMode;
import by.radioegor146.MethodContext;
import by.radioegor146.NativeIntrinsicsMode;
import by.radioegor146.ir.transform.ControlFlowObfuscator;
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
import org.objectweb.asm.tree.MethodNode;

import java.util.Objects;

/**
 * Orchestrates the ASM frontend, selected IR lowering, and shared JNI shell.
 * Frontend/lowering work completes before the shell mutates bytecode, so
 * capability misses remain safe to leave the original method body in Java.
 */
public final class IrMethodCompiler {
    private final AsmToIr frontend;
    private final NativeIntrinsicsMode intrinsicsMode;
    private final MethodLoweringStrategy directStrategy;
    private final MethodLoweringStrategy evaluatorStrategy;
    private final MethodShellEmitter shellEmitter;

    public IrMethodCompiler(MethodShellEmitter shellEmitter) {
        this(shellEmitter, NativeIntrinsicsMode.SAFE);
    }

    public IrMethodCompiler(MethodShellEmitter shellEmitter,
                            NativeIntrinsicsMode intrinsicsMode) {
        this(new AsmToIr(intrinsicsMode), intrinsicsMode,
                new DirectCppStrategy(new IrCppEmitter()),
                new InterpreterStreamStrategy(), shellEmitter);
    }

    IrMethodCompiler(AsmToIr frontend, NativeIntrinsicsMode intrinsicsMode,
                     MethodLoweringStrategy directStrategy,
                     MethodLoweringStrategy evaluatorStrategy,
                     MethodShellEmitter shellEmitter) {
        this.frontend = Objects.requireNonNull(frontend, "frontend");
        this.intrinsicsMode = Objects.requireNonNull(intrinsicsMode, "intrinsicsMode");
        this.directStrategy = Objects.requireNonNull(directStrategy, "directStrategy");
        this.evaluatorStrategy = Objects.requireNonNull(
                evaluatorStrategy, "evaluatorStrategy");
        this.shellEmitter = Objects.requireNonNull(shellEmitter, "shellEmitter");
    }

    public void processMethod(MethodContext context) {
        processMethod(context, IrLoweringMode.DIRECT);
    }

    public void processMethod(MethodContext context, IrLoweringMode loweringMode) {
        processMethod(context, loweringMode, this.intrinsicsMode);
    }

    public void processMethod(MethodContext context, IrLoweringMode loweringMode,
                              NativeIntrinsicsMode methodIntrinsics) {
        processMethod(context, loweringMode, methodIntrinsics,
                ControlFlowObfuscationMode.OFF);
    }

    public void processMethod(MethodContext context, IrLoweringMode loweringMode,
                              NativeIntrinsicsMode methodIntrinsics,
                              ControlFlowObfuscationMode cfObfuscation) {
        AsmToIr methodFrontend =
                Objects.requireNonNull(methodIntrinsics, "methodIntrinsics")
                        == this.intrinsicsMode
                        ? this.frontend : new AsmToIr(methodIntrinsics);
        MethodNode workingMethod =
                JsrRetInliner.prepareForIr(context.clazz, context.method);
        MethodNode bytecodeBody = workingMethod;
        if ("<init>".equals(context.method.name)) {
            // Validate the split before the general frontend so path-sensitive
            // prefix-local diagnostics are reported before any C++ or bridge
            // state is created. The emitted helper starts immediately after the
            // mandatory this/super call retained in bytecode.
            bytecodeBody = ConstructorSpecialMethodProcessor.createNativeBody(
                    context.clazz, workingMethod);
            methodFrontend.build(context.clazz.name, workingMethod);
        }
        String dynamicConstantResolverOwner =
                DynamicConstantSupport.validateResolverInstallation(
                        context.clazz, bytecodeBody,
                        context.obfuscator.getHiddenMethodsPool());
        IrMethod method = methodFrontend.build(
                context.clazz.name, dynamicConstantResolverOwner, bytecodeBody);
        IrLoweringMode selectedMode = Objects.requireNonNull(loweringMode, "loweringMode");
        if (Objects.requireNonNull(cfObfuscation, "cfObfuscation").enabled()
                && selectedMode == IrLoweringMode.DIRECT) {
            ControlFlowObfuscator.apply(method);
        }
        LoweredMethod lowered = lower(method, context, selectedMode);
        DynamicConstantSupport.installResolvers(
                context.clazz, bytecodeBody,
                context.obfuscator.getHiddenMethodsPool());

        if ("<init>".equals(context.method.name)
                && workingMethod != context.method) {
            JsrRetInliner.installCode(context.method, workingMethod);
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

    private LoweredMethod lower(IrMethod method, MethodContext context,
                                IrLoweringMode selectedMode) {
        if (selectedMode != IrLoweringMode.EVAL) {
            return directStrategy.lower(method, new LoweringContext(context));
        }
        try {
            if (method.isSynchronizedMethod()) {
                throw new UnsupportedIrConstructException(
                        "Evaluator lowering does not support synchronized methods");
            }
            return evaluatorStrategy.lower(method, new LoweringContext(context));
        } catch (UnsupportedIrConstructException ignored) {
            return directStrategy.lower(method, new LoweringContext(context));
        }
    }
}
