package by.radioegor146.ir.emit;

import by.radioegor146.MethodContext;
import by.radioegor146.MethodProcessor;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Util;
import by.radioegor146.special.ClInitSpecialMethodProcessor;
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import by.radioegor146.special.DefaultSpecialMethodProcessor;
import by.radioegor146.special.SpecialMethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JNI function shell around an IR-emitted method body.
 */
public final class MethodShellEmitter {
    private final NativeObfuscator obfuscator;

    public MethodShellEmitter(NativeObfuscator obfuscator) {
        this.obfuscator = obfuscator;
    }

    public Shell beginIr(MethodContext context) {
        return begin(context);
    }

    public void finishIr(MethodContext context, Shell shell) {
        finish(context, shell);
    }

    private Shell begin(MethodContext context) {
        MethodNode method = context.method;
        StringBuilder output = context.output;
        SpecialMethodProcessor specialMethodProcessor = getSpecialMethodProcessor(method.name);
        if (specialMethodProcessor == null) {
            throw new RuntimeException(String.format(
                    "Could not find special method processor for %s", method.name));
        }

        output.append("// ").append(Util.escapeCommentString(method.name))
                .append(Util.escapeCommentString(method.desc)).append("\n");

        String methodName = specialMethodProcessor.preProcess(context);
        methodName = "__ngen_" + methodName.replace('/', '_');
        methodName = Util.escapeCppNameString(methodName);
        context.cppNativeMethodName = methodName;

        boolean isStatic = Util.getFlag(method.access, Opcodes.ACC_STATIC);
        context.ret = Type.getReturnType(method.desc);
        Type[] args = Type.getArgumentTypes(method.desc);
        if ("<init>".equals(method.name) && context.proxyMethod != null) {
            Type[] bridgeArgs = Type.getArgumentTypes(
                    context.proxyMethod.getMethodNode().desc);
            if (bridgeArgs.length == 0) {
                throw new IllegalStateException(
                        "Constructor bridge has no receiver argument");
            }
            args = Arrays.copyOfRange(bridgeArgs, 1, bridgeArgs.length);
        }
        context.argTypes = new ArrayList<>(Arrays.asList(args));
        if (!isStatic) {
            context.argTypes.add(0, Type.getType(Object.class));
        }

        if (context.proxyMethod != null) {
            context.nativeMethod = context.proxyMethod.getMethodNode();
            context.nativeMethod.access |= Opcodes.ACC_NATIVE;
        } else {
            context.nativeMethods.append(String.format("            { %s, %s, (void *)&%s },\n",
                    obfuscator.getStringPool().get(context.method.name),
                    obfuscator.getStringPool().get(method.desc), methodName));
        }

        output.append(String.format("%s JNICALL %s(JNIEnv *env, ",
                MethodProcessor.CPP_TYPES[context.ret.getSort()], methodName));
        if (context.proxyMethod != null) {
            output.append("jobject ignored_hidden, ");
        }
        output.append(isStatic ? "jclass clazz" : "jobject obj");

        ArrayList<String> argNames = new ArrayList<>();
        if (!isStatic) {
            argNames.add("obj");
        }
        for (int i = 0; i < args.length; i++) {
            argNames.add("arg" + i);
            output.append(String.format(", %s arg%d",
                    MethodProcessor.CPP_TYPES[args[i].getSort()], i));
        }
        output.append(") {").append("\n");

        boolean hasConstructorClassloaderArgument =
                context.constructorClassloaderArgumentIndex >= 0;
        if (context.proxyMethod != null
                && !hasConstructorClassloaderArgument) {
            output.append("    env->DeleteLocalRef(ignored_hidden);\n");
        }
        if (!isStatic) {
            if (hasConstructorClassloaderArgument) {
                output.append("    jclass clazz = (jclass) arg")
                        .append(context.constructorClassloaderArgumentIndex)
                        .append(";\n");
            } else {
                output.append("    jclass clazz = utils::get_class_from_object(env, obj);\n");
                output.append("    if (env->ExceptionCheck()) { ")
                        .append(String.format("return (%s) 0;",
                                MethodProcessor.CPP_TYPES[context.ret.getSort()]))
                        .append(" }\n");
            }
        }
        output.append("    jobject classloader = utils::get_classloader_from_class(env, clazz);\n");
        output.append("    if (env->ExceptionCheck()) { ")
                .append(String.format("return (%s) 0;",
                        MethodProcessor.CPP_TYPES[context.ret.getSort()]))
                .append(" }\n");
        output.append("    if (classloader == nullptr) { env->FatalError(")
                .append(context.getStringPool().get("classloader == null"))
                .append(String.format("); return (%s) 0; }\n",
                        MethodProcessor.CPP_TYPES[context.ret.getSort()]));
        output.append("\n");
        if (!isStatic) {
            if (!hasConstructorClassloaderArgument) {
                output.append("    env->DeleteLocalRef(clazz);\n");
            }
            output.append("    clazz = utils::find_class_wo_static(env, classloader, ")
                    .append(context.getCachedStrings()
                            .getPointer(context.clazz.name.replace('/', '.')))
                    .append(");\n");
            output.append("    if (env->ExceptionCheck()) { ")
                    .append(String.format("return (%s) 0;",
                            MethodProcessor.CPP_TYPES[context.ret.getSort()]))
                    .append(" }\n");
        }
        output.append("    jobject lookup = nullptr;\n");

        if (method.tryCatchBlocks != null) {
            Set<String> classesForTryCatches = method.tryCatchBlocks.stream()
                    .filter(tryCatchBlock -> tryCatchBlock.type != null)
                    .map(tryCatchBlock -> tryCatchBlock.type)
                    .collect(Collectors.toSet());
            classesForTryCatches.forEach(clazz -> {
                int classId = context.getCachedClasses().getId(clazz);
                context.output.append(String.format("    // try-catch-class %s\n",
                        Util.escapeCommentString(clazz)));
                context.output.append(String.format(
                        "    if (!cclasses[%d] || env->IsSameObject(cclasses[%d], NULL)) { cclasses_mtx[%d].lock(); "
                                + "if (!cclasses[%d] || env->IsSameObject(cclasses[%d], NULL)) { if (jclass clazz = %s) { cclasses[%d] = (jclass) env->NewWeakGlobalRef(clazz); env->DeleteLocalRef(clazz); } } "
                                + "cclasses_mtx[%d].unlock(); if (env->ExceptionCheck()) { return (%s) 0; } }\n",
                        classId, classId, classId, classId, classId,
                        MethodProcessor.getClassGetter(context, clazz), classId, classId,
                        MethodProcessor.CPP_TYPES[context.ret.getSort()]));
            });
        }

        output.append("    std::unordered_set<jobject> refs;\n");
        output.append("\n");
        return new Shell(specialMethodProcessor);
    }

    private void finish(MethodContext context, Shell shell) {
        StringBuilder output = context.output;
        output.append(String.format("    return (%s) 0;\n",
                MethodProcessor.CPP_TYPES[context.ret.getSort()]));

        output.append("}\n\n");
        context.method.localVariables.clear();
        context.method.tryCatchBlocks.clear();
        shell.specialMethodProcessor.postProcess(context);
    }

    private SpecialMethodProcessor getSpecialMethodProcessor(String name) {
        switch (name) {
            case "<init>":
                return new ConstructorSpecialMethodProcessor();
            case "<clinit>":
                return new ClInitSpecialMethodProcessor();
            default:
                return new DefaultSpecialMethodProcessor();
        }
    }

    public static final class Shell {
        private final SpecialMethodProcessor specialMethodProcessor;

        private Shell(SpecialMethodProcessor specialMethodProcessor) {
            this.specialMethodProcessor = specialMethodProcessor;
        }
    }
}
