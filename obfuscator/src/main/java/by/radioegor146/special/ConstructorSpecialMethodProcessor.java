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
import org.objectweb.asm.tree.IincInsnNode;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keeps the verifier-required constructor-chain call in bytecode and moves the
 * fully admitted remainder to a hidden static native bridge.
 */
public final class ConstructorSpecialMethodProcessor implements SpecialMethodProcessor {
    @Override
    public String preProcess(MethodContext context) {
        String name = String.format("special_init_%d_%d",
                context.classIndex, context.methodIndex);
        ConstructorSplit split = split(context.clazz, context.method);
        Type[] constructorArguments = splitArgumentTypes(context.method, split);
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
        InsnList wrapper = cloneRange(
                context.method, 0, split.wrapperEndIndex);
        wrapper.add(new VarInsnNode(Opcodes.ALOAD, 0));

        int local = 1;
        for (Type argument : Type.getArgumentTypes(context.method.desc)) {
            wrapper.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }
        for (ExtraLocal extra : split.extraLocals) {
            wrapper.add(new VarInsnNode(
                    extra.type.getOpcode(Opcodes.ILOAD), extra.index));
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
                constructor.name,
                Type.getMethodDescriptor(Type.VOID_TYPE,
                        splitArgumentTypes(constructor, split)),
                constructor.signature,
                constructor.exceptions == null
                        ? null : constructor.exceptions.toArray(new String[0]));

        Map<LabelNode, LabelNode> labels = labels(constructor);
        for (int i = split.suffixStartIndex;
             i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (!(instruction instanceof FrameNode)) {
                AbstractInsnNode copy = instruction.clone(labels);
                remapSuffixLocal(copy, split);
                body.instructions.add(copy);
                body.maxLocals = Math.max(
                        body.maxLocals, requiredLocalSlots(copy));
            }
        }
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            body.tryCatchBlocks.add(new TryCatchBlockNode(
                    labels.get(tryCatch.start), labels.get(tryCatch.end),
                    labels.get(tryCatch.handler), tryCatch.type));
        }
        body.maxLocals = Math.max(body.maxLocals, constructor.maxLocals);
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

        List<Integer> callIndexes = new ArrayList<>();
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
            callIndexes.add(i);
        }
        if (callIndexes.isEmpty()) {
            throw new UnsupportedIrConstructException(
                    "Constructor has no direct this/super constructor call");
        }

        if (callIndexes.size() > 1) {
            validateNoRepeatedChainCall(constructor, callIndexes);
        }

        int diagnosticCallIndex = callIndexes.get(callIndexes.size() - 1);
        int suffixStartIndex = diagnosticCallIndex + 1;
        int wrapperEndIndex = suffixStartIndex;
        Set<Integer> admittedJoinGotos = new HashSet<>();
        if (callIndexes.size() > 1) {
            SharedSuffix sharedSuffix =
                    sharedSuffix(constructor, callIndexes);
            suffixStartIndex = sharedSuffix.joinIndex;
            wrapperEndIndex =
                    firstExecutableIndex(constructor, suffixStartIndex);
            admittedJoinGotos.addAll(sharedSuffix.gotoIndexes);
        }

        Set<LabelNode> prefixLabels = new HashSet<>();
        for (int i = 0; i < suffixStartIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof LabelNode) {
                prefixLabels.add((LabelNode) instruction);
            }
        }

        Set<Integer> forwardedReferenceLocals = forwardedReferenceLocals(constructor);
        Set<Integer> widenedReferenceLocals = new HashSet<>();
        for (int i = 0; i < suffixStartIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.JSR
                    || instruction.getOpcode() == Opcodes.RET) {
                throw unsupported("Constructor jsr/ret bytecode is not supported",
                        i, instruction);
            }
            // Prefix-local branches keep both edges in the retained bytecode, so
            // the this/super call can still be reached. A branch whose target
            // lands in the suffix would skip the mandatory chain call.
            if (instruction instanceof JumpInsnNode) {
                boolean admittedJoinGoto = admittedJoinGotos.contains(i);
                if (!prefixLabels.contains(((JumpInsnNode) instruction).label)
                        && !admittedJoinGoto) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            } else if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
                if (!prefixLabels.contains(table.dflt)
                        || !prefixLabels.containsAll(table.labels)) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            } else if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                if (!prefixLabels.contains(lookup.dflt)
                        || !prefixLabels.containsAll(lookup.labels)) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            }
            if (instruction.getOpcode() == Opcodes.ASTORE) {
                int local = ((VarInsnNode) instruction).var;
                if (local == 0) {
                    throw unsupported(
                            "Constructor prefix changes local 0 before the bridge",
                            i, instruction);
                }
                if (forwardedReferenceLocals.contains(local)) {
                    widenedReferenceLocals.add(local);
                }
            }
        }

        Set<LabelNode> suffixLabels = new HashSet<>();
        for (int i = suffixStartIndex;
             i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof LabelNode) {
                suffixLabels.add((LabelNode) instruction);
            }
        }
        for (int i = suffixStartIndex;
             i < constructor.instructions.size(); i++) {
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
        validateChainControlFlow(
                constructor, callIndexes, suffixStartIndex);
        List<ExtraLocal> extraLocals =
                extraLocals(
                        constructor, suffixStartIndex, diagnosticCallIndex);
        return new ConstructorSplit(
                suffixStartIndex, wrapperEndIndex,
                widenedReferenceLocals, extraLocals,
                firstExtraLocal(constructor));
    }

    private static SharedSuffix sharedSuffix(
            MethodNode constructor, List<Integer> callIndexes) {
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int nextExecutable = firstExecutableIndex(
                constructor, lastCallIndex + 1);
        if (nextExecutable >= constructor.instructions.size()) {
            throw unsupported(
                    "Constructor chain calls do not share one suffix join",
                    lastCallIndex, constructor.instructions.get(lastCallIndex));
        }

        int joinIndex = -1;
        LabelNode join = null;
        for (int i = lastCallIndex + 1; i < nextExecutable; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof LabelNode) {
                joinIndex = i;
                join = (LabelNode) instruction;
                break;
            }
        }
        if (join == null) {
            throw unsupported(
                    "Constructor chain calls do not share one suffix join",
                    lastCallIndex, constructor.instructions.get(lastCallIndex));
        }

        Set<Integer> gotoIndexes = new HashSet<>();
        for (int i = 0; i < callIndexes.size() - 1; i++) {
            int callIndex = callIndexes.get(i);
            int successorIndex =
                    firstExecutableIndex(constructor, callIndex + 1);
            if (successorIndex >= constructor.instructions.size()) {
                throw unsupported(
                        "Constructor chain calls do not share one suffix join",
                        callIndex, constructor.instructions.get(callIndex));
            }
            AbstractInsnNode successor =
                    constructor.instructions.get(successorIndex);
            if (!(successor instanceof JumpInsnNode)
                    || successor.getOpcode() != Opcodes.GOTO
                    || ((JumpInsnNode) successor).label != join) {
                throw unsupported(
                        "Constructor chain calls do not share one suffix join",
                        callIndex, constructor.instructions.get(callIndex));
            }
            gotoIndexes.add(successorIndex);
        }
        return new SharedSuffix(joinIndex, gotoIndexes);
    }

    private static int firstExecutableIndex(
            MethodNode method, int startIndex) {
        for (int i = startIndex; i < method.instructions.size(); i++) {
            if (method.instructions.get(i).getOpcode() >= 0) {
                return i;
            }
        }
        return method.instructions.size();
    }

    private static void validateNoRepeatedChainCall(
            MethodNode constructor, List<Integer> callIndexes) {
        validateChainCounts(constructor, callIndexes, -1, false);
    }

    private static void validateChainControlFlow(
            MethodNode constructor, List<Integer> callIndexes,
            int suffixStartIndex) {
        validateChainCounts(
                constructor, callIndexes, suffixStartIndex, true);
    }

    private static void validateChainCounts(
            MethodNode constructor, List<Integer> callIndexes,
            int suffixStartIndex, boolean requireCompleteProof) {
        int instructionCount = constructor.instructions.size();
        int[] states = new int[instructionCount];
        boolean[] reachedCalls = new boolean[callIndexes.size()];
        Map<Integer, Integer> callOrdinals = new HashMap<>();
        for (int i = 0; i < callIndexes.size(); i++) {
            callOrdinals.put(callIndexes.get(i), i);
        }
        Map<LabelNode, Integer> labelIndexes = labelIndexes(constructor);
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        states[0] = CHAIN_ZERO;
        pending.add(0);

        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            int input = states[index];
            AbstractInsnNode instruction =
                    constructor.instructions.get(index);

            if (requireCompleteProof && index >= suffixStartIndex
                    && (input & ~CHAIN_ONE) != 0) {
                throw unsupported(
                        "Constructor path reaches the suffix without exactly one "
                                + "this/super call",
                        index, instruction);
            }

            int output = input;
            Integer callOrdinal = callOrdinals.get(index);
            if (callOrdinal != null) {
                reachedCalls[callOrdinal] = true;
                if ((input & (CHAIN_ONE | CHAIN_MANY)) != 0) {
                    throw unsupported(
                            "Constructor path can execute multiple this/super calls",
                            index, instruction);
                }
                output = (input & CHAIN_ZERO) != 0 ? CHAIN_ONE : 0;
            }

            int opcode = instruction.getOpcode();
            if (requireCompleteProof && isReturn(opcode)
                    && output != CHAIN_ONE) {
                throw unsupported(
                        "Constructor return is reachable without exactly one "
                                + "this/super call",
                        index, instruction);
            }

            List<Integer> successors = normalSuccessors(
                    constructor, index, labelIndexes);
            if (requireCompleteProof && successors.isEmpty()
                    && !isReturn(opcode) && opcode != Opcodes.ATHROW
                    && opcode != Opcodes.RET) {
                throw unsupported(
                        "Constructor control flow falls off without returning",
                        index, instruction);
            }
            for (Integer successor : successors) {
                int merged = states[successor] | output;
                if (merged != states[successor]) {
                    states[successor] = merged;
                    pending.add(successor);
                }
            }
        }

        if (requireCompleteProof) {
            for (int i = 0; i < reachedCalls.length; i++) {
                if (!reachedCalls[i]) {
                    int callIndex = callIndexes.get(i);
                    throw unsupported(
                            "Constructor this/super candidate is unreachable",
                            callIndex, constructor.instructions.get(callIndex));
                }
            }
        }
    }

    private static List<Integer> normalSuccessors(
            MethodNode method, int index,
            Map<LabelNode, Integer> labelIndexes) {
        AbstractInsnNode instruction = method.instructions.get(index);
        List<Integer> successors = new ArrayList<>();
        if (instruction instanceof JumpInsnNode) {
            successors.add(requiredLabelIndex(
                    labelIndexes, ((JumpInsnNode) instruction).label,
                    index, instruction));
            if (instruction.getOpcode() != Opcodes.GOTO
                    && instruction.getOpcode() != Opcodes.JSR
                    && index + 1 < method.instructions.size()) {
                successors.add(index + 1);
            }
            return successors;
        }
        if (instruction instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
            successors.add(requiredLabelIndex(
                    labelIndexes, table.dflt, index, instruction));
            for (LabelNode label : table.labels) {
                successors.add(requiredLabelIndex(
                        labelIndexes, label, index, instruction));
            }
            return successors;
        }
        if (instruction instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
            successors.add(requiredLabelIndex(
                    labelIndexes, lookup.dflt, index, instruction));
            for (LabelNode label : lookup.labels) {
                successors.add(requiredLabelIndex(
                        labelIndexes, label, index, instruction));
            }
            return successors;
        }
        int opcode = instruction.getOpcode();
        if (isReturn(opcode) || opcode == Opcodes.ATHROW
                || opcode == Opcodes.RET) {
            return successors;
        }
        if (index + 1 < method.instructions.size()) {
            successors.add(index + 1);
        }
        return successors;
    }

    private static int requiredLabelIndex(
            Map<LabelNode, Integer> labelIndexes, LabelNode label,
            int instructionIndex, AbstractInsnNode instruction) {
        Integer target = labelIndexes.get(label);
        if (target == null) {
            throw unsupported(
                    "Constructor branch target is not in the method",
                    instructionIndex, instruction);
        }
        return target;
    }

    private static Map<LabelNode, Integer> labelIndexes(MethodNode method) {
        Map<LabelNode, Integer> indexes = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof LabelNode) {
                indexes.put((LabelNode) instruction, i);
            }
        }
        return indexes;
    }

    private static boolean isReturn(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
    }

    private static Set<Integer> forwardedReferenceLocals(MethodNode constructor) {
        Set<Integer> locals = new HashSet<>();
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

    private static List<ExtraLocal> extraLocals(
            MethodNode constructor, int suffixStartIndex,
            int diagnosticCallIndex) {
        int firstExtraLocal = firstExtraLocal(constructor);

        Map<Integer, Type> suffixReads = new TreeMap<>();
        for (int i = suffixStartIndex;
             i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            int local = readLocal(instruction);
            Type type = loadType(instruction);
            if (local < firstExtraLocal || type == null) {
                continue;
            }
            Type previous = suffixReads.put(local, type);
            if (previous != null && !previous.equals(type)) {
                throw unsupported(
                        "Constructor suffix reads extra local " + local
                                + " with incompatible types",
                        i, instruction);
            }
        }

        Set<Integer> storedAndRead = new HashSet<>();
        for (int i = 0; i < suffixStartIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            Type type = storeType(instruction);
            if (type != null) {
                int local = ((VarInsnNode) instruction).var;
                if (local >= firstExtraLocal && suffixReads.containsKey(local)) {
                    storedAndRead.add(local);
                }
            }
        }
        if (storedAndRead.isEmpty()) {
            return new ArrayList<>();
        }

        List<ExtraLocal> extras = new ArrayList<>();
        int packedLocal = firstExtraLocal;
        for (Map.Entry<Integer, Type> suffixRead : suffixReads.entrySet()) {
            int local = suffixRead.getKey();
            if (!storedAndRead.contains(local)) {
                continue;
            }
            int state = localStateAtSplit(
                    constructor, suffixStartIndex, local);
            if ((state & LOCAL_UNASSIGNED) != 0 || state == 0) {
                throw unsupported(
                        "Constructor prefix extra local " + local
                                + " is not definitely assigned on every path "
                                + "reaching the this/super call",
                        diagnosticCallIndex,
                        constructor.instructions.get(diagnosticCallIndex));
            }
            Type type = singleStateType(state);
            if (type == null) {
                throw unsupported(
                        "Constructor prefix extra local " + local
                                + " does not have one provable type at the "
                                + "this/super call",
                        diagnosticCallIndex,
                        constructor.instructions.get(diagnosticCallIndex));
            }

            Type suffixType = suffixRead.getValue();
            if (!suffixType.equals(type)) {
                throw unsupported(
                        "Constructor prefix extra local " + local
                                + " is stored as " + type.getDescriptor()
                                + " but read by the suffix as "
                                + suffixType.getDescriptor(),
                        diagnosticCallIndex,
                        constructor.instructions.get(diagnosticCallIndex));
            }
            for (Map.Entry<Integer, Type> otherRead : suffixReads.entrySet()) {
                if (otherRead.getKey() != local
                        && localRangesOverlap(
                        local, type, otherRead.getKey(), otherRead.getValue())) {
                    throw unsupported(
                            "Constructor suffix reads overlapping category-2 "
                                    + "extra local slots at " + local,
                            diagnosticCallIndex,
                            constructor.instructions.get(diagnosticCallIndex));
                }
            }
            extras.add(new ExtraLocal(local, packedLocal, type));
            packedLocal += type.getSize();
        }
        return extras;
    }

    private static int firstExtraLocal(MethodNode constructor) {
        int local = 1;
        for (Type argument : Type.getArgumentTypes(constructor.desc)) {
            local += argument.getSize();
        }
        return local;
    }

    private static boolean localRangesOverlap(
            int leftLocal, Type leftType, int rightLocal, Type rightType) {
        return leftLocal < rightLocal + rightType.getSize()
                && rightLocal < leftLocal + leftType.getSize();
    }

    private static int localStateAtSplit(
            MethodNode constructor, int splitIndex, int local) {
        int[] states = new int[splitIndex + 1];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        states[0] = LOCAL_UNASSIGNED;
        pending.add(0);
        Map<LabelNode, Integer> labelIndexes = new IdentityHashMap<>();
        for (int i = 0; i <= splitIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof LabelNode) {
                labelIndexes.put((LabelNode) instruction, i);
            }
        }

        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            if (index == splitIndex) {
                continue;
            }
            AbstractInsnNode instruction = constructor.instructions.get(index);
            int output = transferLocalState(states[index], instruction, local);
            for (Integer successor :
                    prefixSuccessors(
                            instruction, index, splitIndex, labelIndexes)) {
                int merged = states[successor] | output;
                if (merged != states[successor]) {
                    states[successor] = merged;
                    pending.add(successor);
                }
            }
        }
        return states[splitIndex];
    }

    private static List<Integer> prefixSuccessors(
            AbstractInsnNode instruction, int index, int callIndex,
            Map<LabelNode, Integer> labelIndexes) {
        List<Integer> successors = new ArrayList<>();
        if (instruction instanceof JumpInsnNode) {
            JumpInsnNode jump = (JumpInsnNode) instruction;
            successors.add(labelIndexes.get(jump.label));
            if (instruction.getOpcode() != Opcodes.GOTO
                    && instruction.getOpcode() != Opcodes.JSR
                    && index + 1 <= callIndex) {
                successors.add(index + 1);
            }
            return successors;
        }
        if (instruction instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
            successors.add(labelIndexes.get(table.dflt));
            for (LabelNode label : table.labels) {
                successors.add(labelIndexes.get(label));
            }
            return successors;
        }
        if (instruction instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
            successors.add(labelIndexes.get(lookup.dflt));
            for (LabelNode label : lookup.labels) {
                successors.add(labelIndexes.get(label));
            }
            return successors;
        }
        int opcode = instruction.getOpcode();
        if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)
                || opcode == Opcodes.ATHROW || opcode == Opcodes.RET) {
            return successors;
        }
        if (index + 1 <= callIndex) {
            successors.add(index + 1);
        }
        return successors;
    }

    private static int transferLocalState(
            int input, AbstractInsnNode instruction, int local) {
        Type stored = storeType(instruction);
        if (stored != null) {
            int storedLocal = ((VarInsnNode) instruction).var;
            if (storedLocal == local) {
                return stateForType(stored);
            }
            if (stored.getSize() == 2 && storedLocal + 1 == local) {
                return LOCAL_UNASSIGNED;
            }
            if (storedLocal == local + 1) {
                int output = input & ~(LOCAL_LONG | LOCAL_DOUBLE);
                if ((input & (LOCAL_LONG | LOCAL_DOUBLE)) != 0) {
                    output |= LOCAL_UNASSIGNED;
                }
                return output;
            }
        }
        if (instruction instanceof IincInsnNode
                && ((IincInsnNode) instruction).var == local) {
            int output = input & LOCAL_INT;
            if ((input & ~LOCAL_INT) != 0) {
                output |= LOCAL_UNASSIGNED;
            }
            return output;
        }
        return input;
    }

    private static int readLocal(AbstractInsnNode instruction) {
        if (instruction instanceof IincInsnNode) {
            return ((IincInsnNode) instruction).var;
        }
        return loadType(instruction) == null
                ? -1 : ((VarInsnNode) instruction).var;
    }

    private static Type loadType(AbstractInsnNode instruction) {
        if (instruction instanceof IincInsnNode) {
            return Type.INT_TYPE;
        }
        if (!(instruction instanceof VarInsnNode)) {
            return null;
        }
        switch (instruction.getOpcode()) {
            case Opcodes.ILOAD:
                return Type.INT_TYPE;
            case Opcodes.LLOAD:
                return Type.LONG_TYPE;
            case Opcodes.FLOAD:
                return Type.FLOAT_TYPE;
            case Opcodes.DLOAD:
                return Type.DOUBLE_TYPE;
            case Opcodes.ALOAD:
                return Type.getType(Object.class);
            default:
                return null;
        }
    }

    private static Type storeType(AbstractInsnNode instruction) {
        if (!(instruction instanceof VarInsnNode)) {
            return null;
        }
        switch (instruction.getOpcode()) {
            case Opcodes.ISTORE:
                return Type.INT_TYPE;
            case Opcodes.LSTORE:
                return Type.LONG_TYPE;
            case Opcodes.FSTORE:
                return Type.FLOAT_TYPE;
            case Opcodes.DSTORE:
                return Type.DOUBLE_TYPE;
            case Opcodes.ASTORE:
                return Type.getType(Object.class);
            default:
                return null;
        }
    }

    private static int stateForType(Type type) {
        switch (type.getSort()) {
            case Type.INT:
                return LOCAL_INT;
            case Type.LONG:
                return LOCAL_LONG;
            case Type.FLOAT:
                return LOCAL_FLOAT;
            case Type.DOUBLE:
                return LOCAL_DOUBLE;
            case Type.OBJECT:
                return LOCAL_REFERENCE;
            default:
                throw new IllegalArgumentException(
                        "Unsupported constructor extra-local type " + type);
        }
    }

    private static Type singleStateType(int state) {
        if (state == LOCAL_INT) {
            return Type.INT_TYPE;
        }
        if (state == LOCAL_LONG) {
            return Type.LONG_TYPE;
        }
        if (state == LOCAL_FLOAT) {
            return Type.FLOAT_TYPE;
        }
        if (state == LOCAL_DOUBLE) {
            return Type.DOUBLE_TYPE;
        }
        if (state == LOCAL_REFERENCE) {
            return Type.getType(Object.class);
        }
        return null;
    }

    private static Type[] splitArgumentTypes(
            MethodNode constructor, ConstructorSplit split) {
        Type[] arguments = Type.getArgumentTypes(constructor.desc);
        int local = 1;
        for (int i = 0; i < arguments.length; i++) {
            if (split.widenedReferenceLocals.contains(local)) {
                arguments[i] = Type.getType(Object.class);
            }
            local += arguments[i].getSize();
        }
        Type[] splitArguments =
                new Type[arguments.length + split.extraLocals.size()];
        System.arraycopy(arguments, 0, splitArguments, 0, arguments.length);
        for (int i = 0; i < split.extraLocals.size(); i++) {
            splitArguments[arguments.length + i] =
                    split.extraLocals.get(i).type;
        }
        return splitArguments;
    }

    private static void remapSuffixLocal(
            AbstractInsnNode instruction, ConstructorSplit split) {
        int local;
        if (instruction instanceof IincInsnNode) {
            local = ((IincInsnNode) instruction).var;
        } else if (instruction instanceof VarInsnNode
                && (loadType(instruction) != null
                || storeType(instruction) != null)) {
            local = ((VarInsnNode) instruction).var;
        } else {
            return;
        }
        if (local < split.firstExtraLocal) {
            return;
        }

        // Packed extras occupy the suffix method's trailing parameter slots.
        // Keep unrelated suffix-only locals distinct by moving them after the
        // packed parameters; only this independent clone is rewritten.
        int remapped = split.packedExtraEnd
                + local - split.firstExtraLocal;
        for (ExtraLocal extra : split.extraLocals) {
            if (extra.index == local) {
                remapped = extra.packedIndex;
                break;
            }
        }
        if (instruction instanceof IincInsnNode) {
            ((IincInsnNode) instruction).var = remapped;
        } else {
            ((VarInsnNode) instruction).var = remapped;
        }
    }

    private static int requiredLocalSlots(AbstractInsnNode instruction) {
        if (instruction instanceof IincInsnNode) {
            return ((IincInsnNode) instruction).var + 1;
        }
        Type type = loadType(instruction);
        if (type == null) {
            type = storeType(instruction);
        }
        return type == null
                ? 0 : ((VarInsnNode) instruction).var + type.getSize();
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
        private final int suffixStartIndex;
        private final int wrapperEndIndex;
        private final Set<Integer> widenedReferenceLocals;
        private final List<ExtraLocal> extraLocals;
        private final int firstExtraLocal;
        private final int packedExtraEnd;

        private ConstructorSplit(
                int suffixStartIndex, int wrapperEndIndex,
                Set<Integer> widenedReferenceLocals,
                List<ExtraLocal> extraLocals, int firstExtraLocal) {
            this.suffixStartIndex = suffixStartIndex;
            this.wrapperEndIndex = wrapperEndIndex;
            this.widenedReferenceLocals = widenedReferenceLocals;
            this.extraLocals = extraLocals;
            this.firstExtraLocal = firstExtraLocal;
            int packedLocal = firstExtraLocal;
            for (ExtraLocal extra : extraLocals) {
                packedLocal += extra.type.getSize();
            }
            this.packedExtraEnd = packedLocal;
        }
    }

    private static final class SharedSuffix {
        private final int joinIndex;
        private final Set<Integer> gotoIndexes;

        private SharedSuffix(int joinIndex, Set<Integer> gotoIndexes) {
            this.joinIndex = joinIndex;
            this.gotoIndexes = gotoIndexes;
        }
    }

    private static final class ExtraLocal {
        private final int index;
        private final int packedIndex;
        private final Type type;

        private ExtraLocal(int index, int packedIndex, Type type) {
            this.index = index;
            this.packedIndex = packedIndex;
            this.type = type;
        }
    }

    private static final int LOCAL_UNASSIGNED = 1;
    private static final int LOCAL_INT = 1 << 1;
    private static final int LOCAL_LONG = 1 << 2;
    private static final int LOCAL_FLOAT = 1 << 3;
    private static final int LOCAL_DOUBLE = 1 << 4;
    private static final int LOCAL_REFERENCE = 1 << 5;
    private static final int CHAIN_ZERO = 1;
    private static final int CHAIN_ONE = 1 << 1;
    private static final int CHAIN_MANY = 1 << 2;
}
