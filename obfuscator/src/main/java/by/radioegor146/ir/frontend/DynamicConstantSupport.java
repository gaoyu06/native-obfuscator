package by.radioegor146.ir.frontend;

import by.radioegor146.Platform;
import by.radioegor146.bytecode.LdcPreprocessor;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Admits loadable method and dynamic constants without changing the caller's
 * {@link MethodNode}. Method-handle and method-type constants use the shared
 * LDC preprocessor. A proven constant-dynamic shape becomes a call to a
 * synthetic synchronized resolver installed only after IR lowering succeeds.
 */
public final class DynamicConstantSupport {
    private static final String LOOKUP_DESCRIPTOR =
            "Ljava/lang/invoke/MethodHandles$Lookup;";
    private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";
    private static final String CLASS_DESCRIPTOR = "Ljava/lang/Class;";
    private static final String METHOD_TYPE_DESCRIPTOR = "Ljava/lang/invoke/MethodType;";
    private static final String TYPE_DESCRIPTOR = "Ljava/lang/invoke/TypeDescriptor;";
    private static final String METHOD_HANDLE_DESCRIPTOR =
            "Ljava/lang/invoke/MethodHandle;";
    private static final String OBJECT_DESCRIPTOR = "Ljava/lang/Object;";
    private static final String BOOTSTRAP_ERROR =
            "java/lang/BootstrapMethodError";
    private static final String RESOLVER_PREFIX = "$native$condy$";

    private DynamicConstantSupport() {
    }

    /**
     * Returns the original method when no special LDC is present; otherwise
     * validates every special constant first and transforms a private copy.
     */
    public static MethodNode lower(String owner, MethodNode method) {
        boolean needsCopy = false;
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (!(instruction instanceof LdcInsnNode)) {
                continue;
            }
            Object constant = ((LdcInsnNode) instruction).cst;
            if (constant instanceof ConstantDynamic) {
                validateDynamicConstant((ConstantDynamic) constant, index,
                        Collections.newSetFromMap(
                                new IdentityHashMap<ConstantDynamic, Boolean>()));
                needsCopy = true;
            } else if (constant instanceof Handle) {
                validateMethodHandle((Handle) constant, index);
                needsCopy = true;
            } else if (constant instanceof Type
                    && ((Type) constant).getSort() == Type.METHOD) {
                validateMethodType((Type) constant, index);
                needsCopy = true;
            }
        }
        if (!needsCopy) {
            return method;
        }

        MethodNode copy = copy(method);
        AbstractInsnNode[] copiedInstructions = copy.instructions.toArray();
        for (int index = 0; index < copiedInstructions.length; index++) {
            AbstractInsnNode instruction = copiedInstructions[index];
            if (!(instruction instanceof LdcInsnNode)
                    || !(((LdcInsnNode) instruction).cst
                    instanceof ConstantDynamic)) {
                continue;
            }
            ConstantDynamic constant =
                    (ConstantDynamic) ((LdcInsnNode) instruction).cst;
            copy.instructions.set(instruction, new MethodInsnNode(
                    Opcodes.INVOKESTATIC, owner, resolverName(constant),
                    "()" + constant.getDescriptor(), false));
        }

        ClassNode ownerNode = new ClassNode(Opcodes.ASM9);
        ownerNode.name = owner;
        try {
            new LdcPreprocessor().process(ownerNode, copy, Platform.STD_JAVA);
        } catch (RuntimeException failure) {
            throw new UnsupportedIrConstructException(
                    "MethodHandle/MethodType LDC is not lowerable by the IR frontend: "
                            + failure.getMessage());
        }
        return copy;
    }

    /**
     * Checks class-level constraints and name collisions before lowering can
     * populate JNI caches. No class or method is changed by this check.
     */
    public static void validateResolverInstallation(ClassNode owner, MethodNode method) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (!(instruction instanceof LdcInsnNode)
                    || !(((LdcInsnNode) instruction).cst
                    instanceof ConstantDynamic)) {
                continue;
            }
            if ((owner.access & Opcodes.ACC_INTERFACE) != 0) {
                throw unsupported(
                        "ConstantDynamic LDC in an interface cannot use the IR resolver cache",
                        index);
            }
            ConstantDynamic constant =
                    (ConstantDynamic) ((LdcInsnNode) instruction).cst;
            validateResolverMembers(owner, constant, index,
                    Collections.newSetFromMap(
                            new IdentityHashMap<ConstantDynamic, Boolean>()));
        }
    }

    /**
     * Adds resolver fields and methods after frontend and backend validation
     * have succeeded. Each resolver caches either the value (including null) or
     * the BootstrapMethodError under the class monitor.
     */
    public static void installResolvers(ClassNode owner, MethodNode method) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (!(instruction instanceof LdcInsnNode)
                    || !(((LdcInsnNode) instruction).cst
                    instanceof ConstantDynamic)) {
                continue;
            }
            ConstantDynamic constant =
                    (ConstantDynamic) ((LdcInsnNode) instruction).cst;
            String resolver = resolverName(constant);
            if (!isInstalledResolver(owner, resolver, constant)) {
                installResolver(owner, resolver, constant);
            }
        }
    }

    private static void installResolver(ClassNode owner, String resolver,
                                        ConstantDynamic constant) {
        for (int index = 0;
             index < constant.getBootstrapMethodArgumentCount(); index++) {
            Object argument = constant.getBootstrapMethodArgument(index);
            if (argument instanceof ConstantDynamic) {
                ConstantDynamic nested = (ConstantDynamic) argument;
                String nestedResolver = resolverName(nested);
                if (!isInstalledResolver(owner, nestedResolver, nested)) {
                    installResolver(owner, nestedResolver, nested);
                }
            }
        }

        int fieldAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                | Opcodes.ACC_SYNTHETIC;
        owner.fields.add(new FieldNode(Opcodes.ASM9, fieldAccess,
                stateField(resolver), "I", null, null));
        owner.fields.add(new FieldNode(Opcodes.ASM9, fieldAccess,
                valueField(resolver), constant.getDescriptor(), null, null));
        owner.fields.add(new FieldNode(Opcodes.ASM9, fieldAccess,
                errorField(resolver), "Ljava/lang/BootstrapMethodError;",
                null, null));

        Type constantType = Type.getType(constant.getDescriptor());
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                        | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_SYNTHETIC,
                resolver, "()" + constant.getDescriptor(), null, null);
        LabelNode checkFailure = new LabelNode();
        LabelNode resolve = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode wrapFailure = new LabelNode();
        LabelNode cacheFailure = new LabelNode();

        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name,
                stateField(resolver), "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, checkFailure));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name,
                valueField(resolver), constant.getDescriptor()));
        method.instructions.add(new InsnNode(
                constantType.getOpcode(Opcodes.IRETURN)));

        method.instructions.add(checkFailure);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name,
                stateField(resolver), "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, resolve));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name,
                errorField(resolver), "Ljava/lang/BootstrapMethodError;"));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));

        method.instructions.add(resolve);
        appendBootstrapInvocation(owner.name, method, constant);
        method.instructions.add(new InsnNode(
                constantType.getSize() == 2 ? Opcodes.DUP2 : Opcodes.DUP));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name,
                valueField(resolver), constant.getDescriptor()));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name,
                stateField(resolver), "I"));
        method.instructions.add(tryEnd);
        method.instructions.add(new InsnNode(
                constantType.getOpcode(Opcodes.IRETURN)));

        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(
                Opcodes.INSTANCEOF, BOOTSTRAP_ERROR));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, wrapFailure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(
                Opcodes.CHECKCAST, BOOTSTRAP_ERROR));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, cacheFailure));

        method.instructions.add(wrapFailure);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, BOOTSTRAP_ERROR));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                BOOTSTRAP_ERROR, "<init>", "(Ljava/lang/Throwable;)V", false));

        method.instructions.add(cacheFailure);
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name,
                errorField(resolver), "Ljava/lang/BootstrapMethodError;"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name,
                stateField(resolver), "I"));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));

        method.tryCatchBlocks.add(new TryCatchBlockNode(
                resolve, tryEnd, handler, "java/lang/Throwable"));
        method.maxLocals = 1;
        method.maxStack = bootstrapStackSize(constant);
        owner.methods.add(method);
    }

    private static void appendBootstrapInvocation(String owner, MethodNode method,
                                                  ConstantDynamic constant) {
        Handle bootstrap = constant.getBootstrapMethod();
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/invoke/MethodHandles", "lookup",
                "()Ljava/lang/invoke/MethodHandles$Lookup;", false));
        method.instructions.add(new LdcInsnNode(constant.getName()));
        appendClassLiteral(method, Type.getType(constant.getDescriptor()));
        for (int index = 0;
             index < constant.getBootstrapMethodArgumentCount(); index++) {
            Object argument = constant.getBootstrapMethodArgument(index);
            if (argument instanceof ConstantDynamic) {
                ConstantDynamic nested = (ConstantDynamic) argument;
                method.instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC, owner, resolverName(nested),
                        "()" + nested.getDescriptor(), false));
            } else {
                method.instructions.add(new LdcInsnNode(argument));
            }
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                bootstrap.getOwner(), bootstrap.getName(), bootstrap.getDesc(),
                bootstrap.isInterface()));
    }

    private static void appendClassLiteral(MethodNode method, Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            method.instructions.add(new LdcInsnNode(type));
            return;
        }
        String wrapper;
        switch (type.getSort()) {
            case Type.BOOLEAN:
                wrapper = "java/lang/Boolean";
                break;
            case Type.BYTE:
                wrapper = "java/lang/Byte";
                break;
            case Type.CHAR:
                wrapper = "java/lang/Character";
                break;
            case Type.SHORT:
                wrapper = "java/lang/Short";
                break;
            case Type.INT:
                wrapper = "java/lang/Integer";
                break;
            case Type.FLOAT:
                wrapper = "java/lang/Float";
                break;
            case Type.LONG:
                wrapper = "java/lang/Long";
                break;
            case Type.DOUBLE:
                wrapper = "java/lang/Double";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported constant type " + type);
        }
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, wrapper,
                "TYPE", CLASS_DESCRIPTOR));
    }

    private static int bootstrapStackSize(ConstantDynamic constant) {
        int stack = 3;
        for (Type argument :
                Type.getArgumentTypes(constant.getBootstrapMethod().getDesc())) {
            stack += argument.getSize();
        }
        return Math.max(8, stack);
    }

    private static void validateDynamicConstant(
            ConstantDynamic constant, int bytecodeOffset,
            Set<ConstantDynamic> activeConstants) {
        if (!activeConstants.add(constant)) {
            throw unsupported("Cyclic ConstantDynamic bootstrap arguments",
                    bytecodeOffset);
        }
        try {
            Type constantType;
            Type bootstrapType;
            try {
                constantType = Type.getType(constant.getDescriptor());
                bootstrapType = Type.getMethodType(
                        constant.getBootstrapMethod().getDesc());
            } catch (IllegalArgumentException malformedDescriptor) {
                throw unsupported("Malformed ConstantDynamic descriptor",
                        bytecodeOffset);
            }
            if (!isConstantType(constantType)) {
                throw unsupported(
                        "ConstantDynamic result is not a scalar or reference",
                        bytecodeOffset);
            }

            Handle bootstrap = constant.getBootstrapMethod();
            if (bootstrap.getTag() != Opcodes.H_INVOKESTATIC) {
                throw unsupported(
                        "ConstantDynamic bootstrap is not REF_invokeStatic",
                        bytecodeOffset);
            }
            Type[] parameters = bootstrapType.getArgumentTypes();
            if (parameters.length !=
                    constant.getBootstrapMethodArgumentCount() + 3
                    || !LOOKUP_DESCRIPTOR.equals(parameters[0].getDescriptor())
                    || !STRING_DESCRIPTOR.equals(parameters[1].getDescriptor())
                    || !CLASS_DESCRIPTOR.equals(parameters[2].getDescriptor())) {
                throw unsupported(
                        "ConstantDynamic bootstrap must take Lookup, String, Class, "
                                + "then one exact parameter per static argument",
                        bytecodeOffset);
            }
            if (!constant.getDescriptor().equals(
                    bootstrapType.getReturnType().getDescriptor())) {
                throw unsupported(
                        "ConstantDynamic bootstrap return does not match its constant type",
                        bytecodeOffset);
            }

            for (int index = 0;
                 index < constant.getBootstrapMethodArgumentCount(); index++) {
                Object argument =
                        constant.getBootstrapMethodArgument(index);
                if (argument instanceof ConstantDynamic) {
                    validateDynamicConstant((ConstantDynamic) argument,
                            bytecodeOffset, activeConstants);
                } else if (argument instanceof Handle) {
                    validateMethodHandle((Handle) argument, bytecodeOffset);
                } else if (argument instanceof Type) {
                    Type type = (Type) argument;
                    if (type.getSort() == Type.METHOD) {
                        validateMethodType(type, bytecodeOffset);
                    } else if (type.getSort() != Type.OBJECT
                            && type.getSort() != Type.ARRAY) {
                        throw unsupported(
                                "Primitive Type is not a loadable bootstrap argument",
                                bytecodeOffset);
                    }
                } else if (!(argument instanceof Integer)
                        && !(argument instanceof Long)
                        && !(argument instanceof Float)
                        && !(argument instanceof Double)
                        && !(argument instanceof String)) {
                    throw unsupported(
                            "Unsupported ConstantDynamic bootstrap argument",
                            bytecodeOffset);
                }
                if (!isBootstrapArgumentCompatible(
                        argument, parameters[index + 3])) {
                    throw unsupported(
                            "ConstantDynamic bootstrap argument does not match parameter "
                                    + (index + 3),
                            bytecodeOffset);
                }
            }
        } finally {
            activeConstants.remove(constant);
        }
    }

    private static void validateResolverMembers(
            ClassNode owner, ConstantDynamic constant, int bytecodeOffset,
            Set<ConstantDynamic> visited) {
        if (!visited.add(constant)) {
            return;
        }
        String resolver = resolverName(constant);
        if (!isInstalledResolver(owner, resolver, constant)
                && (hasMethodNamed(owner, resolver)
                || hasField(owner, stateField(resolver))
                || hasField(owner, valueField(resolver))
                || hasField(owner, errorField(resolver)))) {
            throw unsupported(
                    "ConstantDynamic resolver name collides with an existing class member",
                    bytecodeOffset);
        }
        for (int index = 0;
             index < constant.getBootstrapMethodArgumentCount(); index++) {
            Object argument = constant.getBootstrapMethodArgument(index);
            if (argument instanceof ConstantDynamic) {
                validateResolverMembers(owner, (ConstantDynamic) argument,
                        bytecodeOffset, visited);
            }
        }
    }

    private static boolean isBootstrapArgumentCompatible(Object argument,
                                                         Type parameter) {
        if (argument instanceof Integer) {
            return isIntLike(parameter);
        }
        if (argument instanceof Long) {
            return parameter.getSort() == Type.LONG;
        }
        if (argument instanceof Float) {
            return parameter.getSort() == Type.FLOAT;
        }
        if (argument instanceof Double) {
            return parameter.getSort() == Type.DOUBLE;
        }
        if (argument instanceof String) {
            return acceptsReference(parameter, STRING_DESCRIPTOR);
        }
        if (argument instanceof Handle) {
            return acceptsReference(parameter, METHOD_HANDLE_DESCRIPTOR);
        }
        if (argument instanceof Type) {
            Type type = (Type) argument;
            String descriptor = type.getSort() == Type.METHOD
                    ? METHOD_TYPE_DESCRIPTOR : CLASS_DESCRIPTOR;
            return acceptsReference(parameter, descriptor)
                    || type.getSort() == Type.METHOD
                    && TYPE_DESCRIPTOR.equals(parameter.getDescriptor());
        }
        if (argument instanceof ConstantDynamic) {
            return acceptsReference(parameter,
                    ((ConstantDynamic) argument).getDescriptor())
                    || parameter.getDescriptor().equals(
                    ((ConstantDynamic) argument).getDescriptor());
        }
        return false;
    }

    private static boolean acceptsReference(Type parameter,
                                            String actualDescriptor) {
        if (!isReference(parameter)) {
            return false;
        }
        return parameter.getDescriptor().equals(actualDescriptor)
                || OBJECT_DESCRIPTOR.equals(parameter.getDescriptor())
                && (actualDescriptor.startsWith("L")
                || actualDescriptor.startsWith("["));
    }

    private static void validateMethodType(Type type, int bytecodeOffset) {
        try {
            Type.getMethodType(type.getDescriptor());
        } catch (IllegalArgumentException malformedDescriptor) {
            throw unsupported("Malformed MethodType LDC", bytecodeOffset);
        }
    }

    private static void validateMethodHandle(Handle handle, int bytecodeOffset) {
        try {
            switch (handle.getTag()) {
                case Opcodes.H_GETFIELD:
                case Opcodes.H_GETSTATIC:
                case Opcodes.H_PUTFIELD:
                case Opcodes.H_PUTSTATIC:
                    Type fieldType = Type.getType(handle.getDesc());
                    if (fieldType.getSort() == Type.VOID
                            || fieldType.getSort() == Type.METHOD) {
                        throw new IllegalArgumentException();
                    }
                    break;
                case Opcodes.H_INVOKEVIRTUAL:
                case Opcodes.H_INVOKESTATIC:
                case Opcodes.H_INVOKESPECIAL:
                case Opcodes.H_NEWINVOKESPECIAL:
                case Opcodes.H_INVOKEINTERFACE:
                    Type.getMethodType(handle.getDesc());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException malformedHandle) {
            throw unsupported("Unsupported MethodHandle LDC", bytecodeOffset);
        }
    }

    private static boolean isConstantType(Type type) {
        return isIntLike(type) || type.getSort() == Type.LONG
                || type.getSort() == Type.FLOAT
                || type.getSort() == Type.DOUBLE || isReference(type);
    }

    private static boolean isIntLike(Type type) {
        return type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.INT;
    }

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static MethodNode copy(MethodNode method) {
        MethodNode copy = new MethodNode(Opcodes.ASM9, method.access,
                method.name, method.desc, method.signature,
                method.exceptions.toArray(new String[method.exceptions.size()]));
        method.accept(copy);
        return copy;
    }

    private static String resolverName(ConstantDynamic constant) {
        StringBuilder identity = new StringBuilder();
        appendConstantIdentity(identity, constant);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    identity.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                suffix.append(String.format("%02x", digest[index] & 0xff));
            }
            return RESOLVER_PREFIX + suffix;
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendConstantIdentity(
            StringBuilder identity, ConstantDynamic constant) {
        identity.append("condy{").append(constant.getName()).append('\0')
                .append(constant.getDescriptor()).append('\0');
        appendHandleIdentity(identity, constant.getBootstrapMethod());
        for (int index = 0;
             index < constant.getBootstrapMethodArgumentCount(); index++) {
            Object argument = constant.getBootstrapMethodArgument(index);
            identity.append('\0').append(argument.getClass().getName()).append(':');
            if (argument instanceof ConstantDynamic) {
                appendConstantIdentity(identity, (ConstantDynamic) argument);
            } else if (argument instanceof Handle) {
                appendHandleIdentity(identity, (Handle) argument);
            } else if (argument instanceof Type) {
                identity.append(((Type) argument).getDescriptor());
            } else {
                identity.append(argument);
            }
        }
        identity.append('}');
    }

    private static void appendHandleIdentity(StringBuilder identity, Handle handle) {
        identity.append("handle{").append(handle.getTag()).append('\0')
                .append(handle.getOwner()).append('\0')
                .append(handle.getName()).append('\0')
                .append(handle.getDesc()).append('\0')
                .append(handle.isInterface()).append('}');
    }

    private static String stateField(String resolver) {
        return resolver + "$state";
    }

    private static String valueField(String resolver) {
        return resolver + "$value";
    }

    private static String errorField(String resolver) {
        return resolver + "$error";
    }

    private static boolean isInstalledResolver(
            ClassNode owner, String resolver, ConstantDynamic constant) {
        boolean resolverMethod = owner.methods.stream().anyMatch(method ->
                resolver.equals(method.name)
                        && ("()" + constant.getDescriptor()).equals(method.desc)
                        && (method.access & (Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED
                        | Opcodes.ACC_SYNTHETIC))
                        == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                        | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_SYNTHETIC));
        return resolverMethod
                && hasField(owner, stateField(resolver), "I")
                && hasField(owner, valueField(resolver), constant.getDescriptor())
                && hasField(owner, errorField(resolver),
                "Ljava/lang/BootstrapMethodError;");
    }

    private static boolean hasMethodNamed(ClassNode owner, String name) {
        return owner.methods.stream().anyMatch(method -> name.equals(method.name));
    }

    private static boolean hasField(ClassNode owner, String name) {
        return owner.fields.stream().anyMatch(field -> name.equals(field.name));
    }

    private static boolean hasField(
            ClassNode owner, String name, String descriptor) {
        return owner.fields.stream().anyMatch(field ->
                name.equals(field.name) && descriptor.equals(field.desc)
                        && (field.access & (Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC))
                        == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                        | Opcodes.ACC_SYNTHETIC));
    }

    private static UnsupportedIrConstructException unsupported(
            String message, int bytecodeOffset) {
        return new UnsupportedIrConstructException(
                message, bytecodeOffset, Opcodes.LDC);
    }
}
