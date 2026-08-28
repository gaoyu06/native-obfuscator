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

public final class InterpreterMethodProcessor {

    public void processMethod(MethodContext context, InterpreterMethodEmitter.CompiledMethod compiled) {
        MethodNode method = context.method;
        Type[] arguments = Type.getArgumentTypes(method.desc);
        String methodName = "__ngen_" + Util.escapeCppNameString(
                ("native_" + method.name + context.methodIndex).replace('/', '_'));
        String dataName = methodName + "_interp";

        context.cppNativeMethodName = methodName;
        context.ret = Type.getReturnType(method.desc);
        context.argTypes = new ArrayList<>(Arrays.asList(arguments));
        method.access |= Opcodes.ACC_NATIVE;

        context.nativeMethods.append(String.format("            { %s, %s, (void *)&%s },\n",
                context.getStringPool().get(method.name),
                context.getStringPool().get(method.desc), methodName));

        context.output.append("// ").append(Util.escapeCommentString(method.name))
                .append(Util.escapeCommentString(method.desc)).append("\n");
        appendCodeArray(context, dataName, compiled.getCode());
        context.output.append("static const native_jvm::interp::method_desc ").append(dataName)
                .append("_method = { native_jvm::interp::ISA_VERSION, ")
                .append(String.valueOf(compiled.getMaxStack())).append(", ")
                .append(String.valueOf(compiled.getMaxLocals())).append(", ")
                .append(dataName).append("_code, static_cast<std::uint32_t>(sizeof(")
                .append(dataName).append("_code)) };\n\n");

        context.output.append(MethodProcessor.CPP_TYPES[context.ret.getSort()])
                .append(" JNICALL ").append(methodName).append("(JNIEnv *env, jclass clazz");
        for (int i = 0; i < arguments.length; i++) {
            context.output.append(", ").append(MethodProcessor.CPP_TYPES[arguments[i].getSort()])
                    .append(" arg").append(String.valueOf(i));
        }
        context.output.append(") {\n");
        context.output.append("    (void) clazz;\n");
        context.output.append("    std::int32_t interp_stack[")
                .append(String.valueOf(Math.max(1, compiled.getMaxStack()))).append("] = {};\n");
        context.output.append("    std::int32_t interp_locals[")
                .append(String.valueOf(Math.max(1, compiled.getMaxLocals()))).append("] = {};\n");
        for (int i = 0; i < arguments.length; i++) {
            context.output.append("    interp_locals[").append(String.valueOf(i))
                    .append("] = static_cast<std::int32_t>(arg").append(String.valueOf(i))
                    .append(");\n");
        }
        context.output.append("    native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };\n");
        context.output.append("    std::int32_t interp_result = 0;\n");
        context.output.append("    if (!native_jvm::interp::execute_i(").append(dataName)
                .append("_method, interp_frame, &interp_result)) {\n");
        context.output.append("        env->FatalError(\"invalid native_jvm interpreter stream\");\n");
        context.output.append("        return (jint) 0;\n");
        context.output.append("    }\n");
        context.output.append("    return static_cast<jint>(interp_result);\n");
        context.output.append("}\n\n");

        method.instructions.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
    }

    private static void appendCodeArray(MethodContext context, String dataName, byte[] code) {
        context.output.append("static const std::uint8_t ").append(dataName).append("_code[] = { ");
        context.output.append(Arrays.stream(toUnsignedInts(code))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", ")));
        context.output.append(" };\n");
    }

    private static int[] toUnsignedInts(byte[] code) {
        int[] values = new int[code.length];
        for (int i = 0; i < code.length; i++) {
            values[i] = code[i] & 0xff;
        }
        return values;
    }
}
