package by.radioegor146.interpreter;

import by.radioegor146.MethodContext;
import by.radioegor146.MethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;

public final class InterpreterMethodProcessor {

    public void processMethod(MethodContext context, InterpreterMethodEmitter.CompiledMethod compiled) {
        MethodNode method = context.method;
        Type[] arguments = Type.getArgumentTypes(method.desc);
        String suffix = context.classIndex + "_" + context.methodIndex;
        String methodName = "__ngen_i_" + suffix;
        String dataName = "__ngen_b_" + suffix;
        String descriptorName = "__ngen_d_" + suffix;

        context.cppNativeMethodName = methodName;
        context.ret = Type.getReturnType(method.desc);
        context.argTypes = new ArrayList<>(Arrays.asList(arguments));
        method.access |= Opcodes.ACC_NATIVE;

        context.nativeMethods.append(String.format("            { %s, %s, (void *)&%s },\n",
                context.getStringPool().get(method.name),
                context.getStringPool().get(method.desc), methodName));

        appendCodeArray(context, dataName, compiled.getCode());
        context.output.append("static const native_jvm::interp::method_desc ").append(descriptorName)
                .append(" = { native_jvm::interp::ISA_VERSION, ")
                .append(String.valueOf(compiled.getMaxStack())).append(", ")
                .append(String.valueOf(compiled.getMaxLocals())).append(", ")
                .append(dataName).append(", static_cast<std::uint32_t>(sizeof(")
                .append(dataName).append(")) };\n\n");

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
        context.output.append("    if (!native_jvm::interp::execute_i(").append(descriptorName)
                .append(", interp_frame, &interp_result)) {\n");
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
        final char[] hex = "0123456789abcdef".toCharArray();
        context.output.append("static const std::uint8_t ").append(dataName).append("[] = {\n    ");
        for (int i = 0; i < code.length; i++) {
            if (i > 0) {
                context.output.append(i % 16 == 0 ? ",\n    " : ", ");
            }
            int value = code[i] & 0xff;
            context.output.append("0x").append(hex[value >>> 4]).append(hex[value & 0x0f]);
        }
        context.output.append("\n};\n");
    }
}
