package by.radioegor146;

import by.radioegor146.nativeobfuscator.Native;
import by.radioegor146.nativeobfuscator.NativeBackend;
import by.radioegor146.nativeobfuscator.NativeCfObfuscation;
import by.radioegor146.nativeobfuscator.NativeDirectNative;
import by.radioegor146.nativeobfuscator.NativeIntrinsics;
import by.radioegor146.nativeobfuscator.NativeLowering;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Objects;

/**
 * Reads {@link Native} attributes from class and method bytecode. Method
 * values win over class values; {@code INHERIT} keeps the CLI default.
 */
public final class NativeAnnotationSupport {
    static final String NATIVE_DESC = Type.getDescriptor(Native.class);

    private NativeAnnotationSupport() {
    }

    public static final class Options {
        private final IrLoweringMode lowering;
        private final NativeIntrinsicsMode intrinsics;
        private final CompilerBackend backend;
        private final ControlFlowObfuscationMode cfObfuscation;
        private final DirectNativeCallMode directNative;

        Options(IrLoweringMode lowering, NativeIntrinsicsMode intrinsics,
                CompilerBackend backend, ControlFlowObfuscationMode cfObfuscation,
                DirectNativeCallMode directNative) {
            this.lowering = Objects.requireNonNull(lowering, "lowering");
            this.intrinsics = Objects.requireNonNull(intrinsics, "intrinsics");
            this.backend = Objects.requireNonNull(backend, "backend");
            this.cfObfuscation = Objects.requireNonNull(cfObfuscation, "cfObfuscation");
            this.directNative = Objects.requireNonNull(directNative, "directNative");
        }

        public IrLoweringMode getLowering() {
            return lowering;
        }

        public NativeIntrinsicsMode getIntrinsics() {
            return intrinsics;
        }

        public CompilerBackend getBackend() {
            return backend;
        }

        public ControlFlowObfuscationMode getCfObfuscation() {
            return cfObfuscation;
        }

        public DirectNativeCallMode getDirectNative() {
            return directNative;
        }
    }

    public static Options resolve(ClassNode classNode, MethodNode methodNode,
                                  IrLoweringMode cliLowering,
                                  NativeIntrinsicsMode cliIntrinsics,
                                  CompilerBackend cliBackend) {
        return resolve(classNode, methodNode, cliLowering, cliIntrinsics,
                cliBackend, ControlFlowObfuscationMode.OFF);
    }

    public static Options resolve(ClassNode classNode, MethodNode methodNode,
                                  IrLoweringMode cliLowering,
                                  NativeIntrinsicsMode cliIntrinsics,
                                  CompilerBackend cliBackend,
                                  ControlFlowObfuscationMode cliCfObfuscation) {
        return resolve(classNode, methodNode, cliLowering, cliIntrinsics,
                cliBackend, cliCfObfuscation, DirectNativeCallMode.OFF);
    }

    public static Options resolve(ClassNode classNode, MethodNode methodNode,
                                  IrLoweringMode cliLowering,
                                  NativeIntrinsicsMode cliIntrinsics,
                                  CompilerBackend cliBackend,
                                  ControlFlowObfuscationMode cliCfObfuscation,
                                  DirectNativeCallMode cliDirectNative) {
        Parsed classParsed = parse(classNode.invisibleAnnotations);
        Parsed methodParsed = parse(methodNode.invisibleAnnotations);
        return new Options(
                resolveLowering(methodParsed.lowering, classParsed.lowering,
                        cliLowering),
                resolveIntrinsics(methodParsed.intrinsics, classParsed.intrinsics,
                        cliIntrinsics),
                resolveBackend(methodParsed.backend, classParsed.backend,
                        cliBackend),
                resolveCfObfuscation(methodParsed.cfObfuscation,
                        classParsed.cfObfuscation, cliCfObfuscation),
                resolveDirectNative(methodParsed.directNative,
                        classParsed.directNative, cliDirectNative));
    }

    private static final class Parsed {
        NativeLowering lowering = NativeLowering.INHERIT;
        NativeIntrinsics intrinsics = NativeIntrinsics.INHERIT;
        NativeBackend backend = NativeBackend.INHERIT;
        NativeCfObfuscation cfObfuscation = NativeCfObfuscation.INHERIT;
        NativeDirectNative directNative = NativeDirectNative.INHERIT;
    }

    private static Parsed parse(List<AnnotationNode> annotations) {
        Parsed parsed = new Parsed();
        if (annotations == null) {
            return parsed;
        }
        for (AnnotationNode annotation : annotations) {
            if (!NATIVE_DESC.equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                Object key = annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if (!(key instanceof String)) {
                    continue;
                }
                switch ((String) key) {
                    case "lowering":
                        parsed.lowering = enumValue(NativeLowering.class, value,
                                NativeLowering.INHERIT);
                        break;
                    case "intrinsics":
                        parsed.intrinsics = enumValue(NativeIntrinsics.class, value,
                                NativeIntrinsics.INHERIT);
                        break;
                    case "backend":
                        parsed.backend = enumValue(NativeBackend.class, value,
                                NativeBackend.INHERIT);
                        break;
                    case "cfObfuscation":
                        parsed.cfObfuscation = enumValue(NativeCfObfuscation.class,
                                value, NativeCfObfuscation.INHERIT);
                        break;
                    case "directNative":
                        parsed.directNative = enumValue(NativeDirectNative.class,
                                value, NativeDirectNative.INHERIT);
                        break;
                    default:
                        break;
                }
            }
        }
        return parsed;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value,
                                                   E fallback) {
        if (!(value instanceof String[])) {
            return fallback;
        }
        String[] pair = (String[]) value;
        if (pair.length != 2 || pair[1] == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, pair[1]);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static IrLoweringMode resolveLowering(NativeLowering method,
                                                  NativeLowering owner,
                                                  IrLoweringMode cli) {
        NativeLowering chosen = method != NativeLowering.INHERIT ? method : owner;
        if (chosen == NativeLowering.DIRECT) {
            return IrLoweringMode.DIRECT;
        }
        if (chosen == NativeLowering.EVAL) {
            return IrLoweringMode.EVAL;
        }
        return Objects.requireNonNull(cli, "cli");
    }

    private static NativeIntrinsicsMode resolveIntrinsics(NativeIntrinsics method,
                                                          NativeIntrinsics owner,
                                                          NativeIntrinsicsMode cli) {
        NativeIntrinsics chosen = method != NativeIntrinsics.INHERIT ? method : owner;
        if (chosen == NativeIntrinsics.OFF) {
            return NativeIntrinsicsMode.OFF;
        }
        if (chosen == NativeIntrinsics.SAFE) {
            return NativeIntrinsicsMode.SAFE;
        }
        if (chosen == NativeIntrinsics.FAST) {
            return NativeIntrinsicsMode.FAST;
        }
        return Objects.requireNonNull(cli, "cli");
    }

    private static CompilerBackend resolveBackend(NativeBackend method,
                                                  NativeBackend owner,
                                                  CompilerBackend cli) {
        NativeBackend chosen = method != NativeBackend.INHERIT ? method : owner;
        if (chosen == NativeBackend.CPP) {
            return CompilerBackend.CPP;
        }
        if (chosen == NativeBackend.INTERPRETER) {
            return CompilerBackend.INTERPRETER;
        }
        return Objects.requireNonNull(cli, "cli");
    }

    private static ControlFlowObfuscationMode resolveCfObfuscation(
            NativeCfObfuscation method, NativeCfObfuscation owner,
            ControlFlowObfuscationMode cli) {
        NativeCfObfuscation chosen = method != NativeCfObfuscation.INHERIT
                ? method : owner;
        if (chosen == NativeCfObfuscation.OFF) {
            return ControlFlowObfuscationMode.OFF;
        }
        if (chosen == NativeCfObfuscation.BASIC) {
            return ControlFlowObfuscationMode.BASIC;
        }
        return Objects.requireNonNull(cli, "cli");
    }

    private static DirectNativeCallMode resolveDirectNative(
            NativeDirectNative method, NativeDirectNative owner,
            DirectNativeCallMode cli) {
        NativeDirectNative chosen = method != NativeDirectNative.INHERIT
                ? method : owner;
        if (chosen == NativeDirectNative.OFF) {
            return DirectNativeCallMode.OFF;
        }
        if (chosen == NativeDirectNative.ON) {
            return DirectNativeCallMode.ON;
        }
        return Objects.requireNonNull(cli, "cli");
    }
}
