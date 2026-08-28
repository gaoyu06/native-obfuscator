package by.radioegor146.ir.emit;

import by.radioegor146.CatchesBlock;
import by.radioegor146.MethodContext;
import by.radioegor146.MethodProcessor;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Util;
import by.radioegor146.special.ClInitSpecialMethodProcessor;
import by.radioegor146.special.DefaultSpecialMethodProcessor;
import by.radioegor146.special.SpecialMethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared JNI function shell used by both code generators. Legacy-only jvalue
 * slot initialization and catch snippets stay isolated from the IR mode.
 */
public final class MethodShellEmitter {
    private final NativeObfuscator obfuscator;

    public MethodShellEmitter(NativeObfuscator obfuscator) {
        this.obfuscator = obfuscator;
    }

    public Shell beginLegacy(MethodContext context) {
        return begin(context, true);
    }

    public Shell beginIr(MethodContext context) {
        return begin(context, false);
    }

    public void finishLegacy(MethodContext context, Shell shell) {
        finish(context, shell, true);
    }

    public void finishIr(MethodContext context, Shell shell) {
        if (!context.catches.isEmpty()) {
            throw new IllegalStateException("IR phase one cannot emit exception dispatch");
        }
        finish(context, shell, false);
    }

    private Shell begin(MethodContext context, boolean legacyState) {
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

        if (context.proxyMethod != null) {
            output.append("    env->DeleteLocalRef(ignored_hidden);\n");
        }
        if (!isStatic) {
            output.append("    jclass clazz = utils::get_class_from_object(env, obj);\n");
            output.append("    if (env->ExceptionCheck()) { ")
                    .append(String.format("return (%s) 0;",
                            MethodProcessor.CPP_TYPES[context.ret.getSort()]))
                    .append(" }\n");
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
            output.append("    env->DeleteLocalRef(clazz);\n");
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
            for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
                context.getLabelPool().getName(tryCatch.start.getLabel());
                context.getLabelPool().getName(tryCatch.end.getLabel());
                context.getLabelPool().getName(tryCatch.handler.getLabel());
            }
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

        if (legacyState) {
            emitLegacySlotsAndArguments(context, method, argNames);
        } else {
            output.append("    std::unordered_set<jobject> refs;\n");
            output.append("\n");
        }
        return new Shell(specialMethodProcessor);
    }

    private void emitLegacySlotsAndArguments(MethodContext context, MethodNode method,
                                             ArrayList<String> argNames) {
        StringBuilder output = context.output;
        if (method.maxStack > 0) {
            output.append("    jvalue ");
            for (int i = 0; i < method.maxStack; i++) {
                output.append(String.format("cstack%s = {}", i));
                if (i != method.maxStack - 1) {
                    output.append(", ");
                }
            }
            output.append(";\n");
        }
        if (method.maxLocals > 0) {
            output.append("    jvalue ");
            for (int i = 0; i < method.maxLocals; i++) {
                output.append(String.format("clocal%s = {}", i));
                if (i != method.maxLocals - 1) {
                    output.append(", ");
                }
            }
            output.append(";\n");
        }

        output.append("    std::unordered_set<jobject> refs;\n");
        output.append("\n");
        int localIndex = 0;
        for (int i = 0; i < context.argTypes.size(); ++i) {
            Type current = context.argTypes.get(i);
            output.append("    ").append(obfuscator.getSnippets().getSnippet(
                    "LOCAL_LOAD_ARG_" + current.getSort(), Util.createMap(
                            "index", localIndex,
                            "arg", argNames.get(i)
                    ))).append("\n");
            localIndex += current.getSize();
        }
        output.append("\n");

        context.argTypes.forEach(type ->
                context.locals.add(MethodProcessor.TYPE_TO_STACK[type.getSort()]));
        context.stackPointer = 0;
    }

    private void finish(MethodContext context, Shell shell, boolean legacyCatches) {
        StringBuilder output = context.output;
        output.append(String.format("    return (%s) 0;\n",
                MethodProcessor.CPP_TYPES[context.ret.getSort()]));

        if (legacyCatches) {
            emitLegacyCatchDispatch(context);
        }

        output.append("}\n\n");
        context.method.localVariables.clear();
        context.method.tryCatchBlocks.clear();
        shell.specialMethodProcessor.postProcess(context);
    }

    private void emitLegacyCatchDispatch(MethodContext context) {
        StringBuilder output = context.output;
        boolean hasAddedNewBlocks = true;
        Set<CatchesBlock> proceedBlocks = new HashSet<>();

        while (hasAddedNewBlocks) {
            hasAddedNewBlocks = false;
            for (CatchesBlock catchBlock : new ArrayList<>(context.catches.keySet())) {
                if (proceedBlocks.contains(catchBlock)) {
                    continue;
                }
                proceedBlocks.add(catchBlock);
                output.append("    ").append(context.catches.get(catchBlock)).append(": ");
                CatchesBlock.CatchBlock currentCatchBlock = catchBlock.getCatches().get(0);
                if (currentCatchBlock.getClazz() == null) {
                    output.append(context.getSnippets().getSnippet("TRYCATCH_ANY_L",
                            Util.createMap("handler_block", context.getLabelPool()
                                    .getName(currentCatchBlock.getHandler().getLabel()))));
                    output.append("\n");
                    continue;
                }
                output.append(context.getSnippets().getSnippet("TRYCATCH_CHECK_STACK",
                        Util.createMap(
                                "exception_class_ptr", context.getCachedClasses()
                                        .getPointer(currentCatchBlock.getClazz()),
                                "handler_block", context.getLabelPool()
                                        .getName(currentCatchBlock.getHandler().getLabel())
                        )));
                output.append("\n");
                if (catchBlock.getCatches().size() == 1) {
                    output.append("    ");
                    output.append(context.getSnippets().getSnippet("TRYCATCH_END_STACK",
                            Util.createMap("rettype",
                                    MethodProcessor.CPP_TYPES[context.ret.getSort()])));
                    output.append("\n");
                    continue;
                }
                CatchesBlock nextCatchesBlock = new CatchesBlock(catchBlock.getCatches()
                        .stream().skip(1).collect(Collectors.toList()));
                if (context.catches.get(nextCatchesBlock) == null) {
                    context.catches.put(nextCatchesBlock,
                            String.format("L_CATCH_%d", context.catches.size()));
                    hasAddedNewBlocks = true;
                }
                output.append("    ");
                output.append(context.getSnippets().getSnippet("TRYCATCH_ANY_L",
                        Util.createMap("handler_block", context.catches.get(nextCatchesBlock))));
                output.append("\n");
            }
        }
    }

    private SpecialMethodProcessor getSpecialMethodProcessor(String name) {
        switch (name) {
            case "<init>":
                return null;
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
