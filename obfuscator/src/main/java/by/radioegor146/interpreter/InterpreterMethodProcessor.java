package by.radioegor146.interpreter;

import by.radioegor146.MethodContext;
import by.radioegor146.MethodProcessor;
import by.radioegor146.Util;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Emits the opcode stream, method side table, and JNI trampoline for an
 * admitted method.
 */
public final class InterpreterMethodProcessor {

    public void processMethod(MethodContext context,
                              InterpreterMethodEmitter.CompiledMethod compiled) {
        MethodNode method = context.method;
        Type[] arguments = Type.getArgumentTypes(method.desc);
        String methodName = "__ngen_" + Util.escapeCppNameString(
                ("native_" + method.name + context.methodIndex).replace('/', '_'));
        String dataName = methodName + "_interp";

        context.cppNativeMethodName = methodName;
        context.ret = Type.getReturnType(method.desc);
        context.argTypes = new ArrayList<>(Arrays.asList(arguments));
        method.access |= Opcodes.ACC_NATIVE;

        context.nativeMethods.append(String.format(
                "            { %s, %s, (void *)&%s },\n",
                context.getStringPool().get(method.name),
                context.getStringPool().get(method.desc), methodName));

        context.output.append("// ").append(Util.escapeCommentString(method.name))
                .append(Util.escapeCommentString(method.desc)).append("\n");
        appendCodeArray(context, dataName, compiled.getCode());
        InterpreterMethodEmitter.ExceptionHandler[] exceptionHandlers =
                compiled.getExceptionHandlers();
        appendExceptionTable(context, dataName, exceptionHandlers);
        String[] classes = compiled.getClasses();
        appendClassTable(context, dataName, classes);
        InterpreterMethodEmitter.ConstructorReference[] constructors =
                compiled.getConstructors();
        appendConstructorTable(context, dataName, constructors);
        context.output.append("static const native_jvm::interp::method_desc ")
                .append(dataName)
                .append("_method = { ")
                .append(InterpreterMethodEmitter.ISA_VERSION).append(", ")
                .append(compiled.getMaxStack()).append(", ")
                .append(compiled.getMaxLocals()).append(", ")
                .append(dataName)
                .append("_code, static_cast<std::uint32_t>(sizeof(")
                .append(dataName).append("_code)), ")
                .append(exceptionHandlers.length == 0
                        ? "nullptr" : dataName + "_exceptions")
                .append(", ").append(exceptionHandlers.length)
                .append(", ")
                .append(classes.length == 0
                        ? "nullptr" : dataName + "_classes")
                .append(", ").append(classes.length)
                .append(", ")
                .append(constructors.length == 0
                        ? "nullptr" : dataName + "_constructors")
                .append(", ").append(constructors.length)
                .append(" };\n\n");

        context.output.append(MethodProcessor.CPP_TYPES[context.ret.getSort()])
                .append(" JNICALL ").append(methodName)
                .append("(JNIEnv *env, jclass clazz");
        for (int i = 0; i < arguments.length; i++) {
            context.output.append(", ")
                    .append(MethodProcessor.CPP_TYPES[arguments[i].getSort()])
                    .append(" arg").append(i);
        }
        context.output.append(") {\n");
        context.output.append("    (void) clazz;\n");
        context.output.append("    std::int32_t interp_stack[")
                .append(Math.max(1, compiled.getMaxStack())).append("] = {};\n");
        context.output.append("    std::int32_t interp_locals[")
                .append(Math.max(1, compiled.getMaxLocals())).append("] = {};\n");
        context.output.append("    jobject interp_ref_stack[")
                .append(Math.max(1, compiled.getMaxStack())).append("] = {};\n");
        context.output.append("    jobject interp_ref_locals[")
                .append(Math.max(1, compiled.getMaxLocals())).append("] = {};\n");
        int local = 0;
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].getSort() == Type.LONG) {
                context.output.append("    native_jvm::interp::store_long(interp_locals + ")
                        .append(local).append(", static_cast<std::int64_t>(arg")
                        .append(i).append("));\n");
            } else if (isReference(arguments[i])) {
                context.output.append("    interp_ref_locals[").append(local)
                        .append("] = arg").append(i).append(";\n");
            } else {
                context.output.append("    interp_locals[").append(local)
                        .append("] = static_cast<std::int32_t>(arg").append(i)
                        .append(");\n");
            }
            local += arguments[i].getSize();
        }
        context.output.append(
                "    native_jvm::interp::frame interp_frame = { interp_locals, interp_stack, interp_ref_locals, interp_ref_stack };\n");
        boolean returnsLong = context.ret.getSort() == Type.LONG;
        boolean returnsReference = isReference(context.ret);
        if (returnsLong) {
            context.output.append("    std::int64_t interp_result = 0;\n");
        } else if (returnsReference) {
            context.output.append("    jobject interp_result = nullptr;\n");
        } else {
            context.output.append("    std::int32_t interp_result = 0;\n");
        }
        context.output.append(
                "    native_jvm::interp::execution_result interp_status = native_jvm::interp::execute_")
                .append(returnsLong ? "j(" : returnsReference ? "l(" : "i(")
                .append(dataName)
                .append("_method, interp_frame, &interp_result, env);\n");
        context.output.append(
                "    if (interp_status == native_jvm::interp::execution_result::pending_exception) {\n");
        context.output.append(
                "        jthrowable interp_pending = env->ExceptionOccurred();\n");
        context.output.append(
                "        if (interp_pending == nullptr && interp_frame.pending_exception != nullptr) {\n");
        context.output.append(
                "            env->Throw(interp_frame.pending_exception);\n");
        context.output.append("        } else if (interp_pending != nullptr) {\n");
        context.output.append(
                "            env->DeleteLocalRef(interp_pending);\n");
        context.output.append("        } else {\n");
        context.output.append(
                "            env->FatalError(\"missing native_jvm interpreter pending exception\");\n");
        context.output.append("        }\n");
        if (returnsReference) {
            context.output.append("        return nullptr;\n");
        } else {
            context.output.append("        return (")
                    .append(returnsLong ? "jlong" : "jint").append(") 0;\n");
        }
        context.output.append("    }\n");
        context.output.append(
                "    if (interp_status != native_jvm::interp::execution_result::success) {\n");
        context.output.append(
                "        env->FatalError(\"invalid native_jvm interpreter opcode stream\");\n");
        if (returnsReference) {
            context.output.append("        return nullptr;\n");
        } else {
            context.output.append("        return (")
                    .append(returnsLong ? "jlong" : "jint").append(") 0;\n");
        }
        context.output.append("    }\n");
        if (returnsReference) {
            context.output.append("    return reinterpret_cast<")
                    .append(MethodProcessor.CPP_TYPES[context.ret.getSort()])
                    .append(">(interp_result);\n");
        } else {
            context.output.append("    return static_cast<")
                    .append(returnsLong ? "jlong" : "jint")
                    .append(">(interp_result);\n");
        }
        context.output.append("}\n\n");

        method.instructions.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
    }

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static void appendCodeArray(MethodContext context, String dataName,
                                        byte[] code) {
        context.output.append("static const std::uint8_t ")
                .append(dataName).append("_code[] = { ");
        context.output.append(Arrays.stream(toUnsignedInts(code))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", ")));
        context.output.append(" };\n");
    }

    private static void appendExceptionTable(
            MethodContext context, String dataName,
            InterpreterMethodEmitter.ExceptionHandler[] handlers) {
        if (handlers.length == 0) {
            return;
        }
        context.output.append(
                "static const native_jvm::interp::exception_handler ")
                .append(dataName).append("_exceptions[] = {\n");
        for (InterpreterMethodEmitter.ExceptionHandler handler : handlers) {
            context.output.append("    { ")
                    .append(handler.getStartPc()).append(", ")
                    .append(handler.getEndPc()).append(", ")
                    .append(handler.getHandlerPc()).append(", ");
            if (handler.getCatchType() == null) {
                context.output.append("nullptr");
            } else {
                context.output.append(context.getStringPool().get(
                        handler.getCatchType()));
            }
            context.output.append(" },\n");
        }
        context.output.append("};\n");
    }

    private static void appendClassTable(MethodContext context,
                                         String dataName,
                                         String[] classes) {
        if (classes.length == 0) {
            return;
        }
        context.output.append("static const char *const ")
                .append(dataName).append("_classes[] = { ");
        for (int i = 0; i < classes.length; i++) {
            if (i != 0) {
                context.output.append(", ");
            }
            context.output.append(context.getStringPool().get(classes[i]));
        }
        context.output.append(" };\n");
    }

    private static void appendConstructorTable(
            MethodContext context, String dataName,
            InterpreterMethodEmitter.ConstructorReference[] constructors) {
        for (int i = 0; i < constructors.length; i++) {
            Type[] arguments = constructors[i].getArgumentTypes();
            if (arguments.length == 0) {
                continue;
            }
            context.output.append(
                    "static const native_jvm::interp::value_kind ")
                    .append(dataName).append("_constructor_").append(i)
                    .append("_arguments[] = { ");
            for (int argument = 0; argument < arguments.length; argument++) {
                if (argument != 0) {
                    context.output.append(", ");
                }
                context.output.append(
                        "native_jvm::interp::value_kind::")
                        .append(valueKind(arguments[argument]));
            }
            context.output.append(" };\n");
        }
        if (constructors.length == 0) {
            return;
        }
        context.output.append(
                "static const native_jvm::interp::constructor_ref ")
                .append(dataName).append("_constructors[] = {\n");
        for (int i = 0; i < constructors.length; i++) {
            InterpreterMethodEmitter.ConstructorReference constructor =
                    constructors[i];
            Type[] arguments = constructor.getArgumentTypes();
            context.output.append("    { ")
                    .append(constructor.getClassIndex()).append(", ")
                    .append(context.getStringPool().get(
                            constructor.getDescriptor())).append(", ")
                    .append(arguments.length == 0
                            ? "nullptr"
                            : dataName + "_constructor_" + i + "_arguments")
                    .append(", ").append(arguments.length)
                    .append(", ").append(constructor.getArgumentSlots())
                    .append(" },\n");
        }
        context.output.append("};\n");
    }

    private static String valueKind(Type type) {
        if (type.getSort() == Type.LONG) {
            return "i64";
        }
        if (isReference(type)) {
            return "reference";
        }
        return "i32";
    }

    private static int[] toUnsignedInts(byte[] code) {
        int[] values = new int[code.length];
        for (int i = 0; i < code.length; i++) {
            values[i] = code[i] & 0xff;
        }
        return values;
    }
}
