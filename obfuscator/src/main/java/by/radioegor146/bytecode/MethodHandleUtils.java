package by.radioegor146.bytecode;

import by.radioegor146.HiddenMethodsPool;
import by.radioegor146.NativeObfuscator;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class MethodHandleUtils {

    private static AbstractInsnNode getTypeLoadInsnNode(Type type) {
        switch (type.getSort()) {
            case Type.ARRAY:
            case Type.OBJECT:
                return new LdcInsnNode(type);
            case Type.BOOLEAN:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
            case Type.BYTE:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
            case Type.CHAR:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
            case Type.DOUBLE:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
            case Type.FLOAT:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
            case Type.INT:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            case Type.LONG:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
            case Type.SHORT:
                return new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
            default:
                throw new RuntimeException(String.format("Unsupported TypeLoad type: %s", type));
        }
    }

    public static InsnList generateMethodTypeLdcInsn(Type type) {
        if (type.getSort() != Type.METHOD) {
            throw new RuntimeException(String.format("Not a MT: %s", type));
        }
        InsnList insntructions = new InsnList();
        insntructions.add(new LdcInsnNode(type.getDescriptor())); // 5
        insntructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get()); // 6
        insntructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType",
                "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;")); // 5
        return insntructions;
    }

    public static InsnList generateMethodHandleLdcInsn(Handle handle) {
        InsnList instructions = new InsnList();
        instructions.add(PreprocessorUtils.LOOKUP_LOCAL.get()); // 5
        instructions.add(new LdcInsnNode(Type.getObjectType(handle.getOwner()))); // 6
        switch (handle.getTag()) {
            case Opcodes.H_GETFIELD:
            case Opcodes.H_GETSTATIC:
            case Opcodes.H_PUTFIELD:
            case Opcodes.H_PUTSTATIC:
                instructions.add(new LdcInsnNode(handle.getName())); // 7
                instructions.add(getTypeLoadInsnNode(Type.getType(handle.getDesc()))); // 8
                String methodName = "";
                switch (handle.getTag()) {
                    case Opcodes.H_GETFIELD:
                        methodName = "findGetter";
                        break;
                    case Opcodes.H_GETSTATIC:
                        methodName = "findStaticGetter";
                        break;
                    case Opcodes.H_PUTFIELD:
                        methodName = "findSetter";
                        break;
                    case Opcodes.H_PUTSTATIC:
                        methodName = "findStaticSetter";
                        break;
                }
                instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        "java/lang/invoke/MethodHandles$Lookup", methodName,
                        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;")); // 5
                break;
            case Opcodes.H_INVOKEVIRTUAL:
            case Opcodes.H_INVOKEINTERFACE:
                instructions.add(new LdcInsnNode(handle.getName())); // 7
                instructions.add(new LdcInsnNode(handle.getDesc())); // 8
                instructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get()); // 9
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;")); // 8
                instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, // 5
                        "java/lang/invoke/MethodHandles$Lookup", "findVirtual",
                        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
                break;
            case Opcodes.H_INVOKESTATIC:
                instructions.add(new LdcInsnNode(handle.getName())); // 7
                instructions.add(new LdcInsnNode(handle.getDesc())); // 8
                instructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get()); // 9
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;")); // 8
                instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, // 5
                        "java/lang/invoke/MethodHandles$Lookup", "findStatic",
                        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
                break;
            case Opcodes.H_INVOKESPECIAL:
                instructions.add(new LdcInsnNode(handle.getName())); // 7
                instructions.add(new LdcInsnNode(handle.getDesc())); // 8
                instructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get()); // 9
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;")); // 8
                instructions.add(PreprocessorUtils.CLASS_LOCAL.get()); // 9
                instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, // 5
                        "java/lang/invoke/MethodHandles$Lookup", "findSpecial",
                        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"));
                break;
            case Opcodes.H_NEWINVOKESPECIAL:
                instructions.add(new LdcInsnNode(handle.getDesc())); // 7
                instructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get()); // 8
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;")); // 7
                instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, // 5
                        "java/lang/invoke/MethodHandles$Lookup", "findConstructor",
                        "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
                break;
        }
        return instructions;
    }

    public static HiddenMethodsPool.HiddenMethod getInvokeHelper(
            NativeObfuscator obfuscator, ClassNode owner, String invokeName,
            String invokeDescriptor) {
        if (!"invoke".equals(invokeName) && !"invokeExact".equals(invokeName)) {
            throw new IllegalArgumentException(
                    "Unsupported signature-polymorphic invocation " + invokeName);
        }
        boolean exact = "invokeExact".equals(invokeName);
        String targetDescriptor = invokeDescriptor;
        List<Type> helperArguments = new ArrayList<>();
        helperArguments.add(Type.getObjectType("java/lang/invoke/MethodHandle"));
        helperArguments.addAll(Arrays.asList(Type.getArgumentTypes(targetDescriptor)));
        String helperDescriptor = Type.getMethodDescriptor(
                Type.getReturnType(targetDescriptor), helperArguments.toArray(new Type[0]));
        return createInvokeHelper(obfuscator, owner,
                exact ? "mhinvokeexact" : "mhinvoke", helperDescriptor,
                helperDescriptor, targetDescriptor, invokeName, 0);
    }

    public static HiddenMethodsPool.HiddenMethod getInvokeReverseHelper(
            NativeObfuscator obfuscator, String markerDescriptor) {
        Type[] markerArguments = Type.getArgumentTypes(markerDescriptor);
        if (markerArguments.length == 0
                || !"Ljava/lang/invoke/MethodHandle;".equals(
                markerArguments[markerArguments.length - 1].getDescriptor())) {
            throw new IllegalArgumentException(
                    "Invoke-reverse marker must end in a MethodHandle argument");
        }

        Type[] targetArguments = Arrays.copyOf(markerArguments, markerArguments.length - 1);
        String targetDescriptor = simplifyDescriptor(Type.getMethodDescriptor(
                Type.getReturnType(markerDescriptor), targetArguments));
        List<Type> helperArguments = new ArrayList<>(
                Arrays.asList(Type.getArgumentTypes(targetDescriptor)));
        helperArguments.add(Type.getObjectType("java/lang/invoke/MethodHandle"));
        String helperDescriptor = Type.getMethodDescriptor(
                Type.getReturnType(targetDescriptor), helperArguments.toArray(new Type[0]));
        return createInvokeHelper(obfuscator, null,
                "invokereverse", helperDescriptor,
                helperDescriptor, targetDescriptor, "invoke",
                helperArguments.size() - 1);
    }

    private static HiddenMethodsPool.HiddenMethod createInvokeHelper(
            NativeObfuscator obfuscator, ClassNode owner, String helperName,
            String helperDescriptor,
            String cacheKey, String targetDescriptor, String invokeName,
            int methodHandleArgument) {
        Consumer<MethodNode> creator = method -> {
            method.visibleAnnotations = new ArrayList<>();
            method.visibleAnnotations.add(
                    new AnnotationNode("Ljava/lang/invoke/LambdaForm$Hidden;"));
            method.visibleAnnotations.add(
                    new AnnotationNode("Ljdk/internal/vm/annotation/Hidden;"));

            Type[] helperArguments = Type.getArgumentTypes(helperDescriptor);
            int[] localIndexes = new int[helperArguments.length];
            int local = 0;
            for (int i = 0; i < helperArguments.length; i++) {
                localIndexes[i] = local;
                local += helperArguments[i].getSize();
            }

            method.instructions.add(
                    new VarInsnNode(Opcodes.ALOAD, localIndexes[methodHandleArgument]));
            for (int i = 0; i < helperArguments.length; i++) {
                if (i == methodHandleArgument) {
                    continue;
                }
                Type argument = helperArguments[i];
                method.instructions.add(new VarInsnNode(
                        argument.getOpcode(Opcodes.ILOAD), localIndexes[i]));
            }
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandle", invokeName, targetDescriptor));
            method.instructions.add(new InsnNode(
                    Type.getReturnType(targetDescriptor).getOpcode(Opcodes.IRETURN)));
        };
        if (owner != null) {
            return obfuscator.getHiddenMethodsPool().getMethod(
                    owner, helperName, helperDescriptor, creator);
        }
        return obfuscator.getHiddenMethodsPool().getMethod(
                helperName, helperDescriptor, cacheKey, creator);
    }

    private static String simplifyDescriptor(String descriptor) {
        Type returnType = simplifyType(Type.getReturnType(descriptor));
        Type[] argumentTypes = Arrays.stream(Type.getArgumentTypes(descriptor))
                .map(MethodHandleUtils::simplifyType)
                .toArray(Type[]::new);
        return Type.getMethodDescriptor(returnType, argumentTypes);
    }

    private static Type simplifyType(Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            return Type.getObjectType("java/lang/Object");
        }
        if (type.getSort() == Type.METHOD) {
            throw new IllegalArgumentException("Method types are not invocation carriers");
        }
        return type;
    }
}
