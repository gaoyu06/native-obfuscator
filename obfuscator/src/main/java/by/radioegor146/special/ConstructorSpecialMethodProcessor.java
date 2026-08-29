package by.radioegor146.special;

import by.radioegor146.HiddenMethodsPool;
import by.radioegor146.MethodContext;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the verifier-required constructor-chain call in bytecode and moves the
 * fully admitted remainder to a hidden static native bridge.
 */
public final class ConstructorSpecialMethodProcessor implements SpecialMethodProcessor {
    @Override
    public String preProcess(MethodContext context) {
        String name = String.format("special_init_%d_%d",
                context.classIndex, context.methodIndex);
        Type[] constructorArguments = Type.getArgumentTypes(context.method.desc);
        Type[] bridgeArguments = new Type[constructorArguments.length + 1];
        bridgeArguments[0] = Type.getType(Object.class);
        System.arraycopy(constructorArguments, 0, bridgeArguments, 1,
                constructorArguments.length);
        String bridgeDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, bridgeArguments);

        context.proxyMethod = context.obfuscator.getHiddenMethodsPool()
                .getMethod(name, bridgeDescriptor, methodNode -> {
                    methodNode.access = Opcodes.ACC_NATIVE | Opcodes.ACC_PUBLIC
                            | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                            | Opcodes.ACC_BRIDGE;
                    methodNode.visibleAnnotations = new ArrayList<>();
                    methodNode.visibleAnnotations.add(
                            new AnnotationNode("Ljava/lang/invoke/LambdaForm$Hidden;"));
                    methodNode.visibleAnnotations.add(
                            new AnnotationNode("Ljdk/internal/vm/annotation/Hidden;"));
                });
        return name;
    }

    @Override
    public void postProcess(MethodContext context) {
        ConstructorSplit split = split(context.clazz, context.method);
        InsnList wrapper = cloneRange(context.method, 0, split.callIndex + 1);
        wrapper.add(new VarInsnNode(Opcodes.ALOAD, 0));

        int local = 1;
        for (Type argument : Type.getArgumentTypes(context.method.desc)) {
            wrapper.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }

        HiddenMethodsPool.HiddenMethod bridge = context.proxyMethod;
        wrapper.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                bridge.getClassNode().name, bridge.getMethodNode().name,
                bridge.getMethodNode().desc, false));
        wrapper.add(new InsnNode(Opcodes.RETURN));
        context.method.instructions = wrapper;
    }

    /**
     * Produces the initialized-this suffix consumed by the IR frontend without
     * changing the source constructor.
     */
    public static MethodNode createNativeBody(ClassNode owner, MethodNode constructor) {
        ConstructorSplit split = split(owner, constructor);
        MethodNode body = new MethodNode(Opcodes.ASM9,
                constructor.access & ~(Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE),
                constructor.name, constructor.desc, constructor.signature,
                constructor.exceptions == null
                        ? null : constructor.exceptions.toArray(new String[0]));

        Map<LabelNode, LabelNode> labels = labels(constructor);
        for (int i = split.callIndex + 1; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (!(instruction instanceof FrameNode)) {
                body.instructions.add(instruction.clone(labels));
            }
        }
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            body.tryCatchBlocks.add(new TryCatchBlockNode(
                    labels.get(tryCatch.start), labels.get(tryCatch.end),
                    labels.get(tryCatch.handler), tryCatch.type));
        }
        body.maxLocals = constructor.maxLocals;
        body.maxStack = constructor.maxStack;
        return body;
    }

    private static ConstructorSplit split(ClassNode owner, MethodNode constructor) {
        if (!"<init>".equals(constructor.name)
                || Type.getReturnType(constructor.desc).getSort() != Type.VOID
                || (constructor.access & Opcodes.ACC_STATIC) != 0) {
            throw new UnsupportedIrConstructException(
                    "A constructor IR body must be an instance method returning V");
        }

        int callIndex = -1;
        for (int i = 0; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL) {
                continue;
            }
            MethodInsnNode invoke = (MethodInsnNode) instruction;
            if (!"<init>".equals(invoke.name)
                    || (!owner.name.equals(invoke.owner)
                    && (owner.superName == null
                    || !owner.superName.equals(invoke.owner)))) {
                continue;
            }
            if (callIndex >= 0) {
                throw unsupported("Constructor has multiple possible this/super calls",
                        i, instruction);
            }
            callIndex = i;
        }
        if (callIndex < 0) {
            throw new UnsupportedIrConstructException(
                    "Constructor has no direct this/super constructor call");
        }

        Set<Integer> forwardedReferenceLocals = forwardedReferenceLocals(constructor);
        for (int i = 0; i <= callIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode) {
                throw unsupported(
                        "Control flow before the this/super call cannot be split safely",
                        i, instruction);
            }
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && forwardedReferenceLocals.contains(
                    ((VarInsnNode) instruction).var)) {
                throw unsupported(
                        "Constructor prefix changes a reference local forwarded to the bridge",
                        i, instruction);
            }
        }

        Set<LabelNode> suffixLabels = new HashSet<>();
        for (int i = callIndex + 1; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof LabelNode) {
                suffixLabels.add((LabelNode) instruction);
            }
        }
        for (int i = callIndex + 1; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof JumpInsnNode
                    && !suffixLabels.contains(((JumpInsnNode) instruction).label)) {
                throw unsupported("Constructor suffix jumps into its bytecode prefix",
                        i, instruction);
            }
            if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
                if (!suffixLabels.contains(table.dflt)
                        || !suffixLabels.containsAll(table.labels)) {
                    throw unsupported("Constructor switch targets its bytecode prefix",
                            i, instruction);
                }
            }
            if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                if (!suffixLabels.contains(lookup.dflt)
                        || !suffixLabels.containsAll(lookup.labels)) {
                    throw unsupported("Constructor switch targets its bytecode prefix",
                            i, instruction);
                }
            }
        }
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            if (!suffixLabels.contains(tryCatch.start)
                    || !suffixLabels.contains(tryCatch.end)
                    || !suffixLabels.contains(tryCatch.handler)) {
                throw new UnsupportedIrConstructException(
                        "Constructor exception regions may not cross the this/super split");
            }
        }
        return new ConstructorSplit(callIndex);
    }

    private static Set<Integer> forwardedReferenceLocals(MethodNode constructor) {
        Set<Integer> locals = new HashSet<>();
        locals.add(0);
        int local = 1;
        for (Type argument : Type.getArgumentTypes(constructor.desc)) {
            if (argument.getSort() == Type.OBJECT
                    || argument.getSort() == Type.ARRAY) {
                locals.add(local);
            }
            local += argument.getSize();
        }
        return locals;
    }

    private static UnsupportedIrConstructException unsupported(
            String message, int index, AbstractInsnNode instruction) {
        return new UnsupportedIrConstructException(
                message, index, instruction.getOpcode());
    }

    private static InsnList cloneRange(MethodNode method, int start, int end) {
        Map<LabelNode, LabelNode> labels = labels(method);
        InsnList copy = new InsnList();
        for (int i = start; i < end; i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (!(instruction instanceof FrameNode)) {
                copy.add(instruction.clone(labels));
            }
        }
        return copy;
    }

    private static Map<LabelNode, LabelNode> labels(MethodNode method) {
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode) {
                labels.put((LabelNode) instruction, new LabelNode());
            }
        }
        return labels;
    }

    private static final class ConstructorSplit {
        private final int callIndex;

        private ConstructorSplit(int callIndex) {
            this.callIndex = callIndex;
        }
    }
}
