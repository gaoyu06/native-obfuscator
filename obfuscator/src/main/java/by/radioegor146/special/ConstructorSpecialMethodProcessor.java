package by.radioegor146.special;

import by.radioegor146.HiddenMethodsPool;
import by.radioegor146.MethodContext;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
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
    private static final int MAX_DISTINCT_SUFFIXES = 8;
    private static final int MAX_PROVEN_LONG_CHAIN_BINARY_LEVELS = 16;
    private static final int MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS = 16;
    private static final int MAX_PROVEN_DOUBLE_CHAIN_BINARY_LEVELS = 16;
    private static final int MAX_PROVEN_INT_CHAIN_BINARY_LEVELS = 16;

    private List<TryCatchBlockNode> retainedPrefixTryCatches = new ArrayList<>();
    private List<TryCatchBlockNode> retainedSuffixTryCatches = new ArrayList<>();
    private List<RelocatedPrefixHandler> relocatedPrefixHandlers =
            new ArrayList<>();

    @Override
    public String preProcess(MethodContext context) {
        String name = String.format("special_init_%d_%d",
                context.classIndex, context.methodIndex);
        ConstructorSplit split = split(context.clazz, context.method);
        if (split.duplicatedSuffix != null) {
            normalizeDuplicatedSuffix(
                    context.method, split.duplicatedSuffix);
            split = split(context.clazz, context.method);
        }
        retainedPrefixTryCatches =
                new ArrayList<>(split.prefixTryCatches);
        retainedSuffixTryCatches =
                new ArrayList<>(split.suffixTryCatches);
        relocatedPrefixHandlers =
                new ArrayList<>(split.relocatedPrefixHandlers);
        // The JNI shell only needs catch metadata for the native suffix.
        // Prefix catches are restored with cloned wrapper labels in postProcess.
        context.method.tryCatchBlocks.clear();
        context.method.tryCatchBlocks.addAll(split.suffixTryCatches);
        Type[] constructorArguments = splitArgumentTypes(context.method, split);
        if (split.receiverAliasForwarding) {
            context.constructorClassloaderArgumentIndex =
                    constructorArguments.length - 1
                            - (split.distinctSuffix == null ? 0 : 1);
        }
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
        // MethodShellEmitter clears catch metadata after IR lowering. Restore
        // it only long enough to reproduce the proven split, including an
        // isolated tail that is discoverable solely through its handler role.
        context.method.tryCatchBlocks.addAll(retainedPrefixTryCatches);
        context.method.tryCatchBlocks.addAll(retainedSuffixTryCatches);
        ConstructorSplit split = split(context.clazz, context.method);
        context.method.tryCatchBlocks.clear();
        Map<LabelNode, LabelNode> labels = labels(context.method);
        if (split.distinctSuffix != null) {
            InsnList wrapper = new InsnList();
            int retainedStart = 0;
            for (int i = 0;
                 i < split.distinctSuffix.callIndexes.size(); i++) {
                wrapper.add(cloneRange(
                        context.method, retainedStart,
                        split.distinctSuffix.callIndexes.get(i) + 1,
                        labels, relocatedPrefixHandlers));
                appendBridgeInvocation(context, split, wrapper, i);
                retainedStart =
                        split.distinctSuffix.suffixes.get(i).endIndex;
            }
            List<TryCatchBlockNode> wrapperTryCatches = new ArrayList<>();
            for (TryCatchBlockNode tryCatch : retainedPrefixTryCatches) {
                wrapperTryCatches.add(new TryCatchBlockNode(
                        labels.get(tryCatch.start), labels.get(tryCatch.end),
                        labels.get(tryCatch.handler), tryCatch.type));
            }
            context.method.instructions = wrapper;
            context.method.tryCatchBlocks.clear();
            context.method.tryCatchBlocks.addAll(wrapperTryCatches);
            context.method.maxStack = Math.max(
                    context.method.maxStack,
                    bridgeArgumentSlots(context.proxyMethod));
            return;
        }

        InsnList wrapper = cloneRange(
                context.method, 0, split.wrapperEndIndex, labels,
                relocatedPrefixHandlers);
        List<TryCatchBlockNode> wrapperTryCatches = new ArrayList<>();
        for (TryCatchBlockNode tryCatch : retainedPrefixTryCatches) {
            wrapperTryCatches.add(new TryCatchBlockNode(
                    labels.get(tryCatch.start), labels.get(tryCatch.end),
                    labels.get(tryCatch.handler), tryCatch.type));
        }
        appendBridgeInvocation(context, split, wrapper, null);
        context.method.instructions = wrapper;
        context.method.tryCatchBlocks.addAll(wrapperTryCatches);
    }

    private static void appendBridgeInvocation(
            MethodContext context, ConstructorSplit split,
            InsnList wrapper, Integer pathId) {
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
        if (split.receiverAliasForwarding) {
            wrapper.add(new LdcInsnNode(
                    Type.getObjectType(context.clazz.name)));
        }
        if (pathId != null) {
            appendIntConstant(wrapper, pathId);
        }

        HiddenMethodsPool.HiddenMethod bridge = context.proxyMethod;
        wrapper.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                bridge.getClassNode().name, bridge.getMethodNode().name,
                bridge.getMethodNode().desc, false));
        wrapper.add(new InsnNode(Opcodes.RETURN));
    }

    private static void appendIntConstant(InsnList instructions, int value) {
        if (value >= 0 && value <= 5) {
            instructions.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        } else {
            instructions.add(new IntInsnNode(Opcodes.SIPUSH, value));
        }
    }

    private static int bridgeArgumentSlots(
            HiddenMethodsPool.HiddenMethod bridge) {
        int slots = 0;
        for (Type argument :
                Type.getArgumentTypes(bridge.getMethodNode().desc)) {
            slots += argument.getSize();
        }
        return slots;
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
        if (split.distinctSuffix != null) {
            int pathIdLocal = split.packedExtraEnd
                    + (split.receiverAliasForwarding ? 1 : 0);
            body.instructions.add(new VarInsnNode(
                    Opcodes.ILOAD, pathIdLocal));
            int suffixCount = split.distinctSuffix.suffixes.size();
            if (suffixCount == 2) {
                LabelNode secondSuffix = new LabelNode();
                body.instructions.add(new JumpInsnNode(
                        Opcodes.IFNE, secondSuffix));
                appendRelocatedRange(
                        body, constructor, labels, split,
                        split.distinctSuffix.suffixes.get(0).startIndex,
                        split.distinctSuffix.suffixes.get(0).endIndex);
                body.instructions.add(secondSuffix);
                appendRelocatedRange(
                        body, constructor, labels, split,
                        split.distinctSuffix.suffixes.get(1).startIndex,
                        split.distinctSuffix.suffixes.get(1).endIndex);
            } else {
                LabelNode invalidPath = new LabelNode();
                LabelNode[] suffixLabels = new LabelNode[suffixCount];
                for (int i = 0; i < suffixCount; i++) {
                    suffixLabels[i] = new LabelNode();
                }
                body.instructions.add(new TableSwitchInsnNode(
                        0, suffixCount - 1, invalidPath, suffixLabels));
                body.instructions.add(invalidPath);
                body.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                body.instructions.add(new InsnNode(Opcodes.ATHROW));
                for (int i = 0; i < suffixCount; i++) {
                    LinearSuffix suffix =
                            split.distinctSuffix.suffixes.get(i);
                    body.instructions.add(suffixLabels[i]);
                    appendRelocatedRange(
                            body, constructor, labels, split,
                            suffix.startIndex, suffix.endIndex);
                }
            }
            for (RelocatedPrefixHandler handler :
                    split.relocatedPrefixHandlers) {
                appendRelocatedRange(
                        body, constructor, labels, split,
                        handler.startIndex, handler.endIndex);
                if (handler.returnStartIndex >= 0) {
                    appendRelocatedRange(
                            body, constructor, labels, split,
                            handler.returnStartIndex,
                            handler.returnEndIndex);
                }
            }
            for (TryCatchBlockNode tryCatch : split.suffixTryCatches) {
                body.tryCatchBlocks.add(new TryCatchBlockNode(
                        labels.get(tryCatch.start), labels.get(tryCatch.end),
                        labels.get(tryCatch.handler), tryCatch.type));
            }
            body.maxLocals = Math.max(body.maxLocals, pathIdLocal + 1);
            body.maxStack = Math.max(constructor.maxStack, 1);
            return body;
        }

        appendRelocatedRange(
                body, constructor, labels, split,
                split.suffixStartIndex, constructor.instructions.size());
        for (RelocatedPrefixHandler handler :
                split.relocatedPrefixHandlers) {
            appendRelocatedRange(
                    body, constructor, labels, split,
                    handler.startIndex, handler.endIndex);
            if (handler.returnStartIndex >= 0) {
                appendRelocatedRange(
                        body, constructor, labels, split,
                        handler.returnStartIndex, handler.returnEndIndex);
            }
        }
        for (TryCatchBlockNode tryCatch : split.suffixTryCatches) {
            body.tryCatchBlocks.add(new TryCatchBlockNode(
                    labels.get(tryCatch.start), labels.get(tryCatch.end),
                    labels.get(tryCatch.handler), tryCatch.type));
        }
        body.maxLocals = Math.max(
                Math.max(body.maxLocals, constructor.maxLocals),
                split.packedExtraEnd
                        + (split.receiverAliasForwarding ? 1 : 0));
        body.maxStack = constructor.maxStack;
        return body;
    }

    private static void appendRelocatedRange(
            MethodNode body, MethodNode constructor,
            Map<LabelNode, LabelNode> labels, ConstructorSplit split,
            int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            AbstractInsnNode instruction =
                    constructor.instructions.get(i);
            if (!(instruction instanceof FrameNode)) {
                AbstractInsnNode copy = instruction.clone(labels);
                remapSuffixLocal(copy, split);
                body.instructions.add(copy);
                body.maxLocals = Math.max(
                        body.maxLocals, requiredLocalSlots(copy));
            }
        }
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
        Set<Integer> admittedPrefixSuffixBranches = new HashSet<>();
        DuplicatedSuffix duplicatedSuffix = null;
        DistinctSuffix distinctSuffix = null;
        SharedSuffix sharedSuffix = null;
        if (callIndexes.size() > 1) {
            sharedSuffix = sharedSuffix(constructor, callIndexes);
            if (sharedSuffix != null) {
                suffixStartIndex = sharedSuffix.joinIndex;
                wrapperEndIndex =
                        firstExecutableIndex(constructor, suffixStartIndex);
                admittedPrefixSuffixBranches.addAll(
                        sharedSuffix.branchIndexes);
            } else {
                duplicatedSuffix =
                        duplicatedSuffix(constructor, callIndexes);
                if (duplicatedSuffix == null) {
                    distinctSuffix =
                            distinctSuffix(constructor, callIndexes);
                    if (distinctSuffix == null) {
                        throw unsupported(
                                "Constructor chain calls do not share one suffix join",
                                diagnosticCallIndex,
                                constructor.instructions.get(
                                        diagnosticCallIndex));
                    }
                    List<ExtraLocal> extraLocals =
                            distinctExtraLocals(
                                    constructor, distinctSuffix,
                                    diagnosticCallIndex);
                    MultiSuperTryCatches tryCatches =
                            distinctSuffixTryCatches(
                                    constructor, callIndexes, distinctSuffix);
                    boolean receiverAliasForwarding = validateReceiverStores(
                            constructor, constructor.instructions.size(),
                            callIndexes, tryCatches.prefix, true);
                    if (hasReceiverStoreBefore(
                            constructor, callIndexes.get(0))
                            && !receiverAliasForwarding) {
                        throw unsupported(
                                "Constructor path-selected ASTORE 0 does not "
                                        + "provably use receiver-alias forwarding",
                                callIndexes.get(0),
                                constructor.instructions.get(callIndexes.get(0)));
                    }
                    return new ConstructorSplit(
                            distinctSuffix.suffixes.get(0).startIndex,
                            distinctSuffix.suffixes.get(0).startIndex,
                            new HashSet<>(), extraLocals,
                            firstExtraLocal(constructor),
                            tryCatches.prefix, tryCatches.suffix,
                            tryCatches.relocated, receiverAliasForwarding,
                            null, distinctSuffix);
                }
                suffixStartIndex = duplicatedSuffix.canonicalStartIndex;
                wrapperEndIndex = suffixStartIndex;
            }
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
            // the this/super call can still be reached. Cross-split branches are
            // admitted only when sharedSuffix proved their exact post-call
            // shape and validateChainControlFlow later proves one chain call on
            // every suffix/return path.
            if (instruction instanceof JumpInsnNode) {
                boolean admittedPrefixSuffixBranch =
                        admittedPrefixSuffixBranches.contains(i);
                if (!prefixLabels.contains(((JumpInsnNode) instruction).label)
                        && !admittedPrefixSuffixBranch) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            } else if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
                if ((!prefixLabels.contains(table.dflt)
                        || !prefixLabels.containsAll(table.labels))
                        && !admittedPrefixSuffixBranches.contains(i)) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            } else if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                if ((!prefixLabels.contains(lookup.dflt)
                        || !prefixLabels.containsAll(lookup.labels))
                        && !admittedPrefixSuffixBranches.contains(i)) {
                    throw unsupported(
                            "Constructor prefix branches across the this/super call",
                            i, instruction);
                }
            }
            if (instruction.getOpcode() == Opcodes.ASTORE) {
                int local = ((VarInsnNode) instruction).var;
                if (local != 0 && forwardedReferenceLocals.contains(local)) {
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
        List<TryCatchBlockNode> prefixTryCatches = new ArrayList<>();
        List<TryCatchBlockNode> suffixTryCatches = new ArrayList<>();
        Map<LabelNode, Integer> instructionIndexes =
                labelIndexes(constructor);
        Map<LabelNode, RelocatedPrefixHandler> relocatedByLabel =
                new IdentityHashMap<>();
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            boolean entirelyInPrefix =
                    containsTryCatchLabels(prefixLabels, tryCatch);
            boolean entirelyInSuffix =
                    containsTryCatchLabels(suffixLabels, tryCatch);
            if (entirelyInPrefix) {
                prefixTryCatches.add(tryCatch);
            } else if (entirelyInSuffix) {
                suffixTryCatches.add(tryCatch);
            } else {
                RelocatedPrefixHandler relocated =
                        relocatablePrefixHandler(
                                constructor, prefixLabels, suffixLabels,
                                instructionIndexes, suffixStartIndex, tryCatch,
                                null);
                if (relocated == null) {
                    throw new UnsupportedIrConstructException(
                            "Constructor exception regions may not cross the this/super split");
                }
                suffixTryCatches.add(tryCatch);
                relocatedByLabel.put(tryCatch.handler, relocated);
            }
        }
        boolean allowReceiverAliasForwarding =
                callIndexes.size() == 1
                        || duplicatedSuffix != null
                        || sharedSuffix != null && sharedSuffix.strictDiamond;
        boolean receiverAliasForwarding = validateReceiverStores(
                constructor, suffixStartIndex, callIndexes, prefixTryCatches,
                allowReceiverAliasForwarding);
        validateChainControlFlow(
                constructor, callIndexes, suffixStartIndex);
        List<ExtraLocal> extraLocals =
                extraLocals(
                        constructor, suffixStartIndex, diagnosticCallIndex);
        if (sharedSuffix != null
                && !sharedSuffix.prefixExitCallIndexes.isEmpty()
                && !hasConditionalExtraForPrefixExit(
                constructor, suffixStartIndex,
                sharedSuffix.prefixExitCallIndexes, extraLocals)) {
            int callIndex = sharedSuffix.prefixExitCallIndexes.iterator().next();
            throw unsupported(
                    "Constructor immediate prefix return requires an extra local "
                            + "that is unassigned at the exiting this/super call "
                            + "and assigned on every hidden-bridge path",
                    callIndex, constructor.instructions.get(callIndex));
        }
        return new ConstructorSplit(
                suffixStartIndex, wrapperEndIndex,
                widenedReferenceLocals, extraLocals,
                firstExtraLocal(constructor),
                prefixTryCatches, suffixTryCatches,
                new ArrayList<>(relocatedByLabel.values()),
                receiverAliasForwarding,
                duplicatedSuffix, distinctSuffix);
    }

    private static boolean containsTryCatchLabels(
            Set<LabelNode> labels, TryCatchBlockNode tryCatch) {
        return labels.contains(tryCatch.start)
                && labels.contains(tryCatch.end)
                && labels.contains(tryCatch.handler);
    }

    private static Set<LabelNode> labelsInRange(
            MethodNode method, int startIndex, int endIndex) {
        Set<LabelNode> labels = new HashSet<>();
        for (int i = startIndex; i < endIndex; i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof LabelNode) {
                labels.add((LabelNode) instruction);
            }
        }
        return labels;
    }

    private static MultiSuperTryCatches distinctSuffixTryCatches(
            MethodNode constructor, List<Integer> callIndexes,
            DistinctSuffix distinctSuffix) {
        Map<LabelNode, Integer> indexes = labelIndexes(constructor);
        Map<LabelNode, RelocatedPrefixHandler> methodEndHandlers =
                methodEndIsolatedHandlers(constructor, indexes);
        List<TryCatchBlockNode> prefix = new ArrayList<>();
        List<TryCatchBlockNode> suffix = new ArrayList<>();
        Map<LabelNode, RelocatedPrefixHandler> relocatedByLabel =
                new IdentityHashMap<>();
        Set<LabelNode> suffixLabels = new HashSet<>();
        for (LinearSuffix range : distinctSuffix.suffixes) {
            for (int i = range.startIndex; i < range.endIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                if (instruction instanceof LabelNode) {
                    suffixLabels.add((LabelNode) instruction);
                }
            }
        }
        Set<LabelNode> prefixLabels = new HashSet<>();
        prefixLabels.addAll(labelsInRange(
                constructor, 0,
                distinctSuffix.suffixes.get(0).startIndex));
        int firstCallIndex = callIndexes.get(0);
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            if (tryCatchLabelsBefore(
                    tryCatch, indexes, firstCallIndex)) {
                prefix.add(tryCatch);
                continue;
            }
            boolean whollyInOneSuffix = false;
            for (LinearSuffix range : distinctSuffix.suffixes) {
                if (tryCatchLabelsInRange(tryCatch, indexes, range)) {
                    whollyInOneSuffix = true;
                    break;
                }
            }
            if (whollyInOneSuffix) {
                suffix.add(tryCatch);
                continue;
            }

            RelocatedPrefixHandler relocated =
                    relocatablePrefixHandler(
                            constructor, prefixLabels, suffixLabels, indexes,
                            distinctSuffix.suffixes.get(0).startIndex,
                            tryCatch, methodEndHandlers.get(tryCatch.handler));
            boolean protectedInOneSuffix = false;
            if (relocated != null) {
                for (LinearSuffix range : distinctSuffix.suffixes) {
                    if (tryCatchRangeLabelsInRange(
                            tryCatch, indexes, range)) {
                        protectedInOneSuffix = true;
                        break;
                    }
                }
            }
            if (relocated == null || !protectedInOneSuffix) {
                throw new UnsupportedIrConstructException(
                        "Constructor exception regions may not cross "
                                + "the this/super split");
            }
            suffix.add(tryCatch);
            relocatedByLabel.put(tryCatch.handler, relocated);
        }
        return new MultiSuperTryCatches(
                prefix, suffix,
                new ArrayList<>(relocatedByLabel.values()));
    }

    private static boolean tryCatchLabelsBefore(
            TryCatchBlockNode tryCatch, Map<LabelNode, Integer> indexes,
            int endIndex) {
        Integer start = indexes.get(tryCatch.start);
        Integer end = indexes.get(tryCatch.end);
        Integer handler = indexes.get(tryCatch.handler);
        return start != null && end != null && handler != null
                && start < endIndex && end < endIndex
                && handler < endIndex;
    }

    private static boolean tryCatchLabelsInRange(
            TryCatchBlockNode tryCatch, Map<LabelNode, Integer> indexes,
            LinearSuffix range) {
        Integer start = indexes.get(tryCatch.start);
        Integer end = indexes.get(tryCatch.end);
        Integer handler = indexes.get(tryCatch.handler);
        return indexInRange(start, range)
                && indexInRange(end, range)
                && indexInRange(handler, range);
    }

    private static boolean tryCatchRangeLabelsInRange(
            TryCatchBlockNode tryCatch, Map<LabelNode, Integer> indexes,
            LinearSuffix range) {
        Integer start = indexes.get(tryCatch.start);
        Integer end = indexes.get(tryCatch.end);
        return indexInRange(start, range) && indexInRange(end, range);
    }

    private static boolean indexInRange(
            Integer index, LinearSuffix range) {
        return index != null
                && index >= range.startIndex && index < range.endIndex;
    }

    /**
     * Proves the isolated-handler shapes that can be moved without changing
     * their exception behavior. The protected range is wholly in the suffix,
     * and the handler is either in the retained prefix or in one isolated tail
     * immediately after the last suffix. The handler either returns after
     * consuming the caught exception or rethrows that same exception directly
     * or through one exact ASTORE/ALOAD pair. Return handlers may also jump to
     * an equally isolated return. With no extra incoming edge or other range
     * role, the handler and optional return block can be removed from the
     * wrapper and cloned into the suffix with the original table entry.
     */
    private static RelocatedPrefixHandler relocatablePrefixHandler(
            MethodNode constructor,
            Set<LabelNode> prefixLabels, Set<LabelNode> suffixLabels,
            Map<LabelNode, Integer> instructionIndexes,
            int suffixStartIndex,
            TryCatchBlockNode tryCatch,
            RelocatedPrefixHandler methodEndHandler) {
        if (!suffixLabels.contains(tryCatch.start)
                || !suffixLabels.contains(tryCatch.end)
                || (!prefixLabels.contains(tryCatch.handler)
                && methodEndHandler == null)) {
            return null;
        }
        Integer handlerIndex = instructionIndexes.get(tryCatch.handler);
        if (handlerIndex == null
                || hasNormalTarget(constructor, tryCatch.handler)
                || isTryRangeBoundary(constructor, tryCatch.handler)
                || !isOnlyUsedBySuffixRanges(
                        constructor, suffixLabels, tryCatch.handler)) {
            return null;
        }
        if (methodEndHandler != null) {
            return methodEndHandler.startIndex == handlerIndex
                    ? methodEndHandler : null;
        }
        return isolatedHandler(
                constructor, prefixLabels, instructionIndexes,
                suffixStartIndex, tryCatch.handler, false);
    }

    private static RelocatedPrefixHandler isolatedHandler(
            MethodNode constructor, Set<LabelNode> prefixLabels,
            Map<LabelNode, Integer> instructionIndexes,
            int suffixStartIndex, LabelNode handler,
            boolean methodEnd) {
        Integer handlerIndex = instructionIndexes.get(handler);
        if (handlerIndex == null) {
            return null;
        }
        int firstIndex =
                firstExecutableIndex(constructor, handlerIndex + 1);
        int previousIndex =
                previousExecutableIndex(constructor, handlerIndex - 1);
        if (firstIndex >= constructor.instructions.size()
                || previousIndex < 0
                || (methodEnd
                ? canFallThrough(constructor.instructions.get(previousIndex))
                : constructor.instructions.get(previousIndex)
                .getOpcode() != Opcodes.GOTO)
                || !containsOnlyFrames(
                        constructor, handlerIndex + 1, firstIndex)
                || hasNormalTarget(constructor, handler)
                || isTryRangeBoundary(constructor, handler)
                || !handlerInstructionInRegion(
                        firstIndex, handlerIndex,
                        suffixStartIndex, methodEnd)) {
            return null;
        }
        AbstractInsnNode first = constructor.instructions.get(firstIndex);
        if (first.getOpcode() == Opcodes.ATHROW) {
            return completeIsolatedHandler(
                    constructor,
                    new RelocatedPrefixHandler(
                            handlerIndex, firstIndex + 1),
                    methodEnd);
        }

        boolean popsException = first.getOpcode() == Opcodes.POP;
        Integer storedExceptionLocal =
                relocatableCaughtExceptionLocal(constructor, first);
        if (!popsException && storedExceptionLocal == null) {
            return null;
        }
        int successorIndex =
                firstExecutableIndex(constructor, firstIndex + 1);
        if (successorIndex >= constructor.instructions.size()
                || !containsOnlyFrames(
                        constructor, firstIndex + 1, successorIndex)) {
            return null;
        }
        AbstractInsnNode successor =
                constructor.instructions.get(successorIndex);
        if (storedExceptionLocal != null
                && successor.getOpcode() == Opcodes.ALOAD) {
            VarInsnNode reload = (VarInsnNode) successor;
            int throwIndex =
                    firstExecutableIndex(constructor, successorIndex + 1);
            if (reload.var != storedExceptionLocal
                    || !handlerInstructionInRegion(
                    successorIndex, handlerIndex,
                    suffixStartIndex, methodEnd)
                    || !handlerInstructionInRegion(
                    throwIndex, handlerIndex,
                    suffixStartIndex, methodEnd)
                    || throwIndex >= constructor.instructions.size()
                    || constructor.instructions.get(throwIndex)
                    .getOpcode() != Opcodes.ATHROW
                    || !containsOnlyFrames(
                    constructor, successorIndex + 1, throwIndex)) {
                return null;
            }
            return completeIsolatedHandler(
                    constructor,
                    new RelocatedPrefixHandler(
                            handlerIndex, throwIndex + 1),
                    methodEnd);
        }
        if (successor.getOpcode() == Opcodes.RETURN) {
            if (!handlerInstructionInRegion(
                    successorIndex, handlerIndex,
                    suffixStartIndex, methodEnd)) {
                return null;
            }
            return completeIsolatedHandler(
                    constructor,
                    new RelocatedPrefixHandler(
                            handlerIndex, successorIndex + 1),
                    methodEnd);
        }
        if (!(successor instanceof JumpInsnNode)
                || successor.getOpcode() != Opcodes.GOTO) {
            return null;
        }

        JumpInsnNode jump = (JumpInsnNode) successor;
        LabelNode returnLabel = jump.label;
        Integer returnLabelIndex = instructionIndexes.get(returnLabel);
        if ((!methodEnd && !prefixLabels.contains(returnLabel))
                || returnLabelIndex == null
                || returnLabelIndex <= successorIndex
                || !handlerInstructionInRegion(
                successorIndex, handlerIndex,
                suffixStartIndex, methodEnd)
                || !handlerInstructionInRegion(
                returnLabelIndex, handlerIndex,
                suffixStartIndex, methodEnd)
                || !containsOnlyFrames(
                        constructor, successorIndex + 1,
                        returnLabelIndex)) {
            return null;
        }
        int returnIndex =
                firstExecutableIndex(constructor, returnLabelIndex + 1);
        int returnPreviousIndex =
                previousExecutableIndex(constructor, returnLabelIndex - 1);
        if (!handlerInstructionInRegion(
                returnIndex, handlerIndex, suffixStartIndex, methodEnd)
                || returnIndex >= constructor.instructions.size()
                || constructor.instructions.get(returnIndex)
                .getOpcode() != Opcodes.RETURN
                || !containsOnlyFrames(
                        constructor, returnLabelIndex + 1, returnIndex)
                || hasNormalTargetOtherThan(
                        constructor, returnLabel, jump)
                || isTryRangeBoundary(constructor, returnLabel)
                || isExceptionHandler(constructor, returnLabel)
                || (returnPreviousIndex >= 0
                && canFallThrough(
                        constructor.instructions.get(returnPreviousIndex)))) {
            return null;
        }
        return completeIsolatedHandler(
                constructor,
                new RelocatedPrefixHandler(
                        handlerIndex, successorIndex + 1,
                        returnLabelIndex, returnIndex + 1),
                methodEnd);
    }

    private static boolean handlerInstructionInRegion(
            int index, int handlerIndex, int suffixStartIndex,
            boolean methodEnd) {
        return methodEnd
                ? index > handlerIndex
                : index < suffixStartIndex;
    }

    private static RelocatedPrefixHandler completeIsolatedHandler(
            MethodNode constructor, RelocatedPrefixHandler handler,
            boolean methodEnd) {
        if (!methodEnd) {
            return handler;
        }
        int endIndex = handler.returnEndIndex >= 0
                ? handler.returnEndIndex : handler.endIndex;
        return firstExecutableIndex(constructor, endIndex)
                == constructor.instructions.size() ? handler : null;
    }

    private static Map<LabelNode, RelocatedPrefixHandler>
    methodEndIsolatedHandlers(
            MethodNode constructor,
            Map<LabelNode, Integer> instructionIndexes) {
        Map<LabelNode, RelocatedPrefixHandler> handlers =
                new IdentityHashMap<>();
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            if (handlers.containsKey(tryCatch.handler)) {
                continue;
            }
            RelocatedPrefixHandler handler = isolatedHandler(
                    constructor, new HashSet<>(), instructionIndexes,
                    0, tryCatch.handler, true);
            if (handler != null) {
                handlers.put(tryCatch.handler, handler);
            }
        }
        return handlers;
    }

    private static Integer relocatableCaughtExceptionLocal(
            MethodNode method, AbstractInsnNode instruction) {
        if (instruction.getOpcode() != Opcodes.ASTORE) {
            return null;
        }
        int local = ((VarInsnNode) instruction).var;
        return local != 0 && !isCategoryTwoHole(method, local)
                ? local : null;
    }

    private static boolean isCategoryTwoHole(MethodNode method, int local) {
        int argumentLocal = 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (argument.getSize() == 2 && local == argumentLocal + 1) {
                return true;
            }
            argumentLocal += argument.getSize();
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof VarInsnNode)) {
                continue;
            }
            int opcode = instruction.getOpcode();
            if ((opcode == Opcodes.LLOAD || opcode == Opcodes.LSTORE
                    || opcode == Opcodes.DLOAD || opcode == Opcodes.DSTORE)
                    && local == ((VarInsnNode) instruction).var + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOnlyFrames(
            MethodNode method, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!(method.instructions.get(i) instanceof FrameNode)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNormalTarget(
            MethodNode method, LabelNode target) {
        return hasNormalTargetOtherThan(method, target, null);
    }

    private static boolean hasNormalTargetOtherThan(
            MethodNode method, LabelNode target,
            JumpInsnNode allowedJump) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode
                    && ((JumpInsnNode) instruction).label == target
                    && instruction != allowedJump) {
                return true;
            }
            if (instruction instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table =
                        (TableSwitchInsnNode) instruction;
                if (table.dflt == target || table.labels.contains(target)) {
                    return true;
                }
            }
            if (instruction instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup =
                        (LookupSwitchInsnNode) instruction;
                if (lookup.dflt == target || lookup.labels.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canFallThrough(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (instruction instanceof TableSwitchInsnNode
                || instruction instanceof LookupSwitchInsnNode
                || opcode == Opcodes.GOTO
                || isReturn(opcode)
                || opcode == Opcodes.ATHROW
                || opcode == Opcodes.RET) {
            return false;
        }
        return true;
    }

    private static boolean isExceptionHandler(
            MethodNode method, LabelNode label) {
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            if (tryCatch.handler == label) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnlyUsedBySuffixRanges(
            MethodNode method, Set<LabelNode> suffixLabels,
            LabelNode handler) {
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            if (tryCatch.handler == handler
                    && (!suffixLabels.contains(tryCatch.start)
                    || !suffixLabels.contains(tryCatch.end))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTryRangeBoundary(
            MethodNode method, LabelNode label) {
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            if (tryCatch.start == label || tryCatch.end == label) {
                return true;
            }
        }
        return false;
    }

    private static SharedSuffix sharedSuffix(
            MethodNode constructor, List<Integer> callIndexes) {
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int nextExecutable = firstExecutableIndex(
                constructor, lastCallIndex + 1);
        if (nextExecutable >= constructor.instructions.size()) {
            return null;
        }

        if (callIndexes.size() == 2
                && constructor.instructions.get(nextExecutable).getOpcode()
                != Opcodes.RETURN) {
            int prefixExitSuccessor = firstExecutableIndex(
                    constructor, callIndexes.get(0) + 1);
            if (isImmediatePrefixReturn(
                    constructor, callIndexes, 0, prefixExitSuccessor)) {
                Set<Integer> prefixExitCallIndexes = new HashSet<>();
                prefixExitCallIndexes.add(callIndexes.get(0));
                return new SharedSuffix(
                        lastCallIndex + 1, new HashSet<>(),
                        prefixExitCallIndexes, false);
            }
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
            return null;
        }

        Set<Integer> branchIndexes = new HashSet<>();
        boolean strictDiamond = true;
        for (int i = 0; i < callIndexes.size() - 1; i++) {
            int callIndex = callIndexes.get(i);
            int successorIndex =
                    firstExecutableIndex(constructor, callIndex + 1);
            if (successorIndex >= constructor.instructions.size()) {
                return null;
            }
            AbstractInsnNode successor =
                    constructor.instructions.get(successorIndex);
            if (successor instanceof JumpInsnNode
                    && successor.getOpcode() == Opcodes.GOTO
                    && ((JumpInsnNode) successor).label == join) {
                branchIndexes.add(successorIndex);
                continue;
            }

            Integer conditionalBranch = conditionalJoinOrReturnBranch(
                    constructor, callIndexes, i, join);
            if (conditionalBranch != null) {
                branchIndexes.add(conditionalBranch);
                strictDiamond = false;
                continue;
            }

            Set<Integer> switchBranches = switchJoinOrReturnBranches(
                    constructor, callIndexes, i, join, joinIndex);
            if (switchBranches == null) {
                return null;
            }
            branchIndexes.addAll(switchBranches);
            strictDiamond = false;
        }
        return new SharedSuffix(
                joinIndex, branchIndexes, new HashSet<>(), strictDiamond);
    }

    /**
     * Proves the prefix exit used by conditionally assigned extra-local
     * forwarding. Exactly two chain calls are present: the first returns
     * immediately, while the final call falls through to the shared suffix.
     * The later extra-local proof additionally requires a local that is
     * unassigned at this call but has one type on every path to the bridge.
     */
    private static boolean isImmediatePrefixReturn(
            MethodNode constructor, List<Integer> callIndexes,
            int callOrdinal, int successorIndex) {
        return callIndexes.size() == 2
                && callOrdinal == 0
                && constructor.tryCatchBlocks.isEmpty()
                && successorIndex < callIndexes.get(1)
                && constructor.instructions.get(successorIndex).getOpcode()
                == Opcodes.RETURN
                && hasEmptyChainEntryStacks(constructor, callIndexes);
    }

    /**
     * Proves one non-GOTO prefix-to-suffix edge. For exactly two chain calls,
     * the first call may compare one or two directly loaded declared int-family
     * arguments, select the shared suffix, and otherwise return immediately:
     *
     * <pre>
     * invokespecial owner-or-super.&lt;init&gt;
     * iload declaredArgument
     * [iload declaredArgument]
     * if&lt;int-condition&gt; sharedSuffix
     * return
     * </pre>
     *
     * The admitted conditions are the JVM unary int-zero and binary int-compare
     * jump families. The no-handler and empty-entry-stack restrictions keep
     * this a local control-flow proof. The full chain-count analysis
     * subsequently proves that the join and every return are reached after
     * exactly one chain call.
     */
    private static Integer conditionalJoinOrReturnBranch(
            MethodNode constructor, List<Integer> callIndexes,
            int callOrdinal, LabelNode join) {
        if (callIndexes.size() != 2 || callOrdinal != 0
                || !constructor.tryCatchBlocks.isEmpty()
                || !hasEmptyChainEntryStacks(constructor, callIndexes)) {
            return null;
        }

        int firstLoadIndex = firstExecutableIndex(
                constructor, callIndexes.get(callOrdinal) + 1);
        if (firstLoadIndex >= constructor.instructions.size()) {
            return null;
        }
        AbstractInsnNode firstLoad =
                constructor.instructions.get(firstLoadIndex);
        if (firstLoad.getOpcode() != Opcodes.ILOAD
                || !isDeclaredIntArgument(
                constructor, ((VarInsnNode) firstLoad).var)) {
            return null;
        }

        int branchIndex =
                firstExecutableIndex(constructor, firstLoadIndex + 1);
        if (branchIndex >= constructor.instructions.size()) {
            return null;
        }
        AbstractInsnNode branch = constructor.instructions.get(branchIndex);
        if (!(branch instanceof JumpInsnNode)
                || !isUnaryIntCompare(branch.getOpcode())) {
            AbstractInsnNode secondLoad = branch;
            if (secondLoad.getOpcode() != Opcodes.ILOAD
                    || !isDeclaredIntArgument(
                    constructor, ((VarInsnNode) secondLoad).var)) {
                return null;
            }
            branchIndex =
                    firstExecutableIndex(constructor, branchIndex + 1);
            if (branchIndex >= constructor.instructions.size()) {
                return null;
            }
            branch = constructor.instructions.get(branchIndex);
            if (!(branch instanceof JumpInsnNode)
                    || !isBinaryIntCompare(branch.getOpcode())) {
                return null;
            }
        }
        if (((JumpInsnNode) branch).label != join) {
            return null;
        }

        int returnIndex = firstExecutableIndex(constructor, branchIndex + 1);
        if (returnIndex >= callIndexes.get(1)
                || constructor.instructions.get(returnIndex).getOpcode()
                != Opcodes.RETURN) {
            return null;
        }
        return branchIndex;
    }

    private static boolean isUnaryIntCompare(int opcode) {
        return opcode >= Opcodes.IFEQ && opcode <= Opcodes.IFLE;
    }

    private static boolean isBinaryIntCompare(int opcode) {
        return opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ICMPLE;
    }

    /**
     * Proves the switch counterpart of conditionalJoinOrReturnBranch. The exact
     * admitted shape has one declared int-family argument load immediately
     * after the first of exactly two chain calls:
     *
     * <pre>
     * invokespecial owner-or-super.&lt;init&gt;
     * iload declaredArgument
     * tableswitch/lookupswitch {
     *   sharedSuffix
     *   immediateReturn
     *   directGotoSharedSuffix
     * }
     * </pre>
     *
     * Every non-suffix target must remain before the second chain call and
     * execute only RETURN or GOTO to the exact shared suffix as its first
     * executable instruction. The full chain-count proof subsequently rejects
     * any zero-call suffix/return path or path that invokes a second chain
     * constructor.
     */
    private static Set<Integer> switchJoinOrReturnBranches(
            MethodNode constructor, List<Integer> callIndexes,
            int callOrdinal, LabelNode join, int joinIndex) {
        if (callIndexes.size() != 2 || callOrdinal != 0
                || !constructor.tryCatchBlocks.isEmpty()
                || !hasEmptyChainEntryStacks(constructor, callIndexes)) {
            return null;
        }

        int loadIndex = firstExecutableIndex(
                constructor, callIndexes.get(callOrdinal) + 1);
        if (loadIndex >= constructor.instructions.size()) {
            return null;
        }
        AbstractInsnNode load = constructor.instructions.get(loadIndex);
        if (load.getOpcode() != Opcodes.ILOAD
                || !isDeclaredIntArgument(
                constructor, ((VarInsnNode) load).var)) {
            return null;
        }

        int switchIndex = firstExecutableIndex(constructor, loadIndex + 1);
        if (switchIndex >= constructor.instructions.size()) {
            return null;
        }
        AbstractInsnNode switchInstruction =
                constructor.instructions.get(switchIndex);
        List<LabelNode> targets = new ArrayList<>();
        if (switchInstruction instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode table =
                    (TableSwitchInsnNode) switchInstruction;
            targets.add(table.dflt);
            targets.addAll(table.labels);
        } else if (switchInstruction instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lookup =
                    (LookupSwitchInsnNode) switchInstruction;
            targets.add(lookup.dflt);
            targets.addAll(lookup.labels);
        } else {
            return null;
        }

        Map<LabelNode, Integer> indexes = labelIndexes(constructor);
        Set<Integer> admittedBranches = new HashSet<>();
        admittedBranches.add(switchIndex);
        boolean reachesSharedSuffix = false;
        int secondCallIndex = callIndexes.get(1);
        for (LabelNode target : targets) {
            if (target == join) {
                reachesSharedSuffix = true;
                continue;
            }

            Integer targetIndex = indexes.get(target);
            if (targetIndex == null || targetIndex <= switchIndex
                    || targetIndex >= joinIndex) {
                return null;
            }
            int executableIndex =
                    firstExecutableIndex(constructor, targetIndex + 1);
            if (executableIndex >= secondCallIndex) {
                return null;
            }
            AbstractInsnNode executable =
                    constructor.instructions.get(executableIndex);
            if (executable.getOpcode() == Opcodes.RETURN) {
                continue;
            }
            if (executable instanceof JumpInsnNode
                    && executable.getOpcode() == Opcodes.GOTO
                    && ((JumpInsnNode) executable).label == join) {
                admittedBranches.add(executableIndex);
                reachesSharedSuffix = true;
                continue;
            }
            return null;
        }
        return reachesSharedSuffix ? admittedBranches : null;
    }

    private static boolean isDeclaredIntArgument(
            MethodNode constructor, int candidateLocal) {
        int local = 1;
        for (Type argument : Type.getArgumentTypes(constructor.desc)) {
            if (local == candidateLocal) {
                int sort = argument.getSort();
                return sort == Type.BOOLEAN || sort == Type.BYTE
                        || sort == Type.CHAR || sort == Type.SHORT
                        || sort == Type.INT;
            }
            local += argument.getSize();
        }
        return false;
    }

    /**
     * Proves one additional multi-call shape: every call falls through to a
     * straight-line, structurally identical suffix copy ending in RETURN. The
     * copy may be empty, making RETURN the immediate successor of each call.
     *
     * <p>For three or more calls, every call must additionally receive the
     * original receiver, either directly or through a syntactically admitted
     * prefix alias, and locally proven argument inputs. The canonical final
     * copy can then be shared by replacing every earlier copy with a GOTO;
     * this does not create multiple native exits.
     */
    private static DuplicatedSuffix duplicatedSuffix(
            MethodNode constructor, List<Integer> callIndexes) {
        if (callIndexes.size() < 2) {
            return null;
        }

        List<LinearSuffix> suffixes = new ArrayList<>();
        for (int i = 0; i < callIndexes.size(); i++) {
            LinearSuffix suffix =
                    linearSuffix(constructor, callIndexes.get(i));
            if (suffix == null
                    || (i + 1 < callIndexes.size()
                    && suffix.endIndex > callIndexes.get(i + 1))) {
                return null;
            }
            suffixes.add(suffix);
        }
        LinearSuffix canonical = suffixes.get(suffixes.size() - 1);
        Map<LabelNode, Integer> indexes = labelIndexes(constructor);
        Set<LabelNode> canonicalTailHandlers =
                canonicalSuffixTailHandlers(
                        constructor, canonical, indexes);
        if (canonical.endIndex != constructor.instructions.size()
                && canonicalTailHandlers == null) {
            return null;
        }

        for (LinearSuffix suffix : suffixes) {
            if (!sameLinearSuffix(constructor, suffix, canonical)) {
                return null;
            }
        }

        boolean hasPrefixReceiverAlias = false;
        int firstCallIndex = callIndexes.get(0);
        Set<Integer> declaredReferenceLocals =
                forwardedReferenceLocals(constructor);
        for (int i = 0; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.ASTORE
                    || ((VarInsnNode) instruction).var != 0) {
                continue;
            }
            if (i >= firstCallIndex) {
                return null;
            }
            int valueIndex =
                    previousExecutableIndex(constructor, i - 1);
            if (valueIndex < 0) {
                return null;
            }
            AbstractInsnNode value =
                    constructor.instructions.get(valueIndex);
            boolean identityStore = value.getOpcode() == Opcodes.ALOAD
                    && ((VarInsnNode) value).var == 0;
            if (value.getOpcode() == Opcodes.DUP) {
                int duplicatedValueIndex =
                        previousExecutableIndex(constructor, valueIndex - 1);
                AbstractInsnNode duplicatedValue = duplicatedValueIndex < 0
                        ? null
                        : constructor.instructions.get(duplicatedValueIndex);
                identityStore = duplicatedValueIndex >= 0
                        && duplicatedValue.getOpcode() == Opcodes.ALOAD
                        && ((VarInsnNode) duplicatedValue).var == 0;
            }
            if (identityStore) {
                continue;
            }
            if (value.getOpcode() != Opcodes.ACONST_NULL
                    && (value.getOpcode() != Opcodes.ALOAD
                    || !declaredReferenceLocals.contains(
                    ((VarInsnNode) value).var))) {
                return null;
            }
            hasPrefixReceiverAlias = true;
        }
        if (callIndexes.size() > 2) {
            if (!hasDirectDeclaredChainInputs(
                    constructor, callIndexes, hasPrefixReceiverAlias)) {
                return null;
            }
        }

        if (!hasProvenDuplicatedSuffixExtras(
                constructor, callIndexes, canonical)) {
            return null;
        }
        if (!hasEmptyChainEntryStacks(constructor, callIndexes)) {
            return null;
        }
        Set<LabelNode> canonicalLabels = labelsInRange(
                constructor, canonical.startIndex,
                constructor.instructions.size());
        Set<LabelNode> prefixLabels = labelsInRange(
                constructor, 0, callIndexes.get(0));
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            if (tryCatchLabelsBefore(
                    tryCatch, indexes, callIndexes.get(0))) {
                continue;
            }
            if (tryCatchRangeLabelsInRange(
                    tryCatch, indexes, canonical)
                    && canonicalTailHandlers != null
                    && canonicalTailHandlers.contains(tryCatch.handler)) {
                continue;
            }
            RelocatedPrefixHandler relocated =
                    relocatablePrefixHandler(
                            constructor, prefixLabels, canonicalLabels,
                            indexes, canonical.startIndex, tryCatch, null);
            if (relocated != null
                    && tryCatchRangeLabelsInRange(
                    tryCatch, indexes, canonical)) {
                continue;
            }
            throw new UnsupportedIrConstructException(
                    "Constructor exception regions may not cross "
                            + "the this/super split");
        }

        List<DuplicatedRange> discarded = new ArrayList<>();
        for (int i = 0; i < suffixes.size() - 1; i++) {
            LinearSuffix suffix = suffixes.get(i);
            discarded.add(new DuplicatedRange(
                    callIndexes.get(i), suffix.startIndex, suffix.endIndex));
        }
        return new DuplicatedSuffix(
                discarded, callIndexes.get(callIndexes.size() - 1),
                canonical.startIndex);
    }

    /**
     * Proves the only executable tail accepted after the canonical copy's
     * normal RETURN: one suffix-owned handler matching the same six isolated
     * return/rethrow forms used by handler relocation. The normal RETURN makes
     * the handler unreachable by fallthrough; the generic split later clones
     * the handler and its table into the independent IR body.
     */
    private static Set<LabelNode> canonicalSuffixTailHandlers(
            MethodNode constructor, LinearSuffix canonical,
            Map<LabelNode, Integer> indexes) {
        if (canonical.endIndex == constructor.instructions.size()) {
            return new HashSet<>();
        }

        Set<LabelNode> handlers = new HashSet<>();
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            Integer handlerIndex = indexes.get(tryCatch.handler);
            if (handlerIndex != null
                    && handlerIndex >= canonical.endIndex) {
                handlers.add(tryCatch.handler);
            }
        }
        if (handlers.size() != 1) {
            return null;
        }

        LabelNode handler = handlers.iterator().next();
        Map<LabelNode, RelocatedPrefixHandler> isolatedHandlers =
                methodEndIsolatedHandlers(constructor, indexes);
        RelocatedPrefixHandler isolated = isolatedHandlers.get(handler);
        if (isolated == null
                || isolated.startIndex != canonical.endIndex) {
            return null;
        }
        return handlers;
    }

    /**
     * Keeps copied-suffix normalization fail-closed for extra locals. A
     * canonical-suffix read must have a real store before the first chain call,
     * and that value must have one compatible type at every call that will
     * reach the normalized join. Stores inside a suffix copy do not qualify:
     * earlier copies are discarded by normalization.
     */
    private static boolean hasProvenDuplicatedSuffixExtras(
            MethodNode constructor, List<Integer> callIndexes,
            LinearSuffix canonical) {
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Type> suffixReads = new TreeMap<>();
        for (int i = canonical.startIndex; i < canonical.endIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            int local = readLocal(instruction);
            if (local < firstExtraLocal) {
                continue;
            }
            Type type = loadType(instruction);
            Type previous = suffixReads.put(local, type);
            if (type == null || previous != null && !previous.equals(type)) {
                return false;
            }
        }
        if (suffixReads.isEmpty()) {
            return true;
        }

        Set<Integer> prefixStores = new HashSet<>();
        for (int i = 0; i < callIndexes.get(0); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (storeType(instruction) == null) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            if (local >= firstExtraLocal) {
                prefixStores.add(local);
            }
        }

        for (Map.Entry<Integer, Type> suffixRead : suffixReads.entrySet()) {
            int local = suffixRead.getKey();
            if (!prefixStores.contains(local)) {
                return false;
            }
            int[] states = localStates(constructor, local);
            for (Integer callIndex : callIndexes) {
                int state = states[callIndex];
                if ((state & LOCAL_UNASSIGNED) != 0 || state == 0
                        || !suffixRead.getValue().equals(
                        singleStateType(state))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Proves a bounded multi-call form whose nonempty suffixes contain at least
     * two different CFGs. A suffix may be straight-line or contain one proven
     * int-family conditional or switch whose closed, forward CFG reaches only
     * RETURN. Repeated suffix CFGs remain separate path-id ranges.
     * Every call site keeps locally visible receiver/argument inputs, and
     * every complete path reaches RETURN after exactly one call. The
     * independent body receives one trailing int selector after any proven
     * prefix extra-local parameters.
     */
    private static DistinctSuffix distinctSuffix(
            MethodNode constructor, List<Integer> callIndexes) {
        if (callIndexes.size() < 2
                || callIndexes.size() > MAX_DISTINCT_SUFFIXES) {
            return null;
        }

        boolean hasPrefixReceiverStore = false;
        int firstCallIndex = callIndexes.get(0);
        Set<Integer> declaredReferenceLocals =
                forwardedReferenceLocals(constructor);
        for (int i = 0; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.JSR
                    || instruction.getOpcode() == Opcodes.RET) {
                return null;
            }
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 0) {
                if (i >= firstCallIndex) {
                    return null;
                }
                int valueIndex =
                        previousExecutableIndex(constructor, i - 1);
                if (valueIndex < 0) {
                    return null;
                }
                AbstractInsnNode value =
                        constructor.instructions.get(valueIndex);
                if (value.getOpcode() != Opcodes.ACONST_NULL
                        && (value.getOpcode() != Opcodes.ALOAD
                        || !declaredReferenceLocals.contains(
                        ((VarInsnNode) value).var))) {
                    return null;
                }
                hasPrefixReceiverStore = true;
            }
        }

        Map<LabelNode, Integer> instructionIndexes =
                labelIndexes(constructor);
        Map<LabelNode, RelocatedPrefixHandler> methodEndHandlers =
                methodEndIsolatedHandlers(
                        constructor, instructionIndexes);
        Set<LabelNode> methodEndHandlerLabels =
                methodEndHandlers.keySet();
        List<LinearSuffix> suffixes = new ArrayList<>();
        for (int i = 0; i < callIndexes.size(); i++) {
            LinearSuffix suffix =
                    boundedDistinctSuffix(
                            constructor, callIndexes.get(i),
                            methodEndHandlerLabels);
            if (suffix == null || executableInstructionCount(
                    constructor, suffix) <= 1
                    || (i + 1 < callIndexes.size()
                    && suffix.endIndex > callIndexes.get(i + 1))) {
                return null;
            }
            suffixes.add(suffix);
        }
        LinearSuffix finalSuffix =
                suffixes.get(suffixes.size() - 1);
        boolean endsBeforeIsolatedHandler =
                methodEndHandlers.size() == 1
                        && methodEndHandlers.values().iterator().next()
                        .startIndex == finalSuffix.endIndex;
        if ((finalSuffix.endIndex != constructor.instructions.size()
                && !endsBeforeIsolatedHandler)
                || !hasDirectDeclaredChainInputs(
                        constructor, callIndexes, hasPrefixReceiverStore)
                || !hasEmptyChainEntryStacks(constructor, callIndexes)) {
            return null;
        }
        boolean hasDistinctPair = false;
        for (int i = 0; i < suffixes.size(); i++) {
            for (int j = i + 1; j < suffixes.size(); j++) {
                if (!sameBoundedSuffix(
                        constructor, suffixes.get(i), suffixes.get(j))) {
                    hasDistinctPair = true;
                }
            }
        }
        if (!hasDistinctPair) {
            return null;
        }

        validateChainCounts(
                constructor, callIndexes,
                constructor.instructions.size(), true);
        return new DistinctSuffix(
                new ArrayList<>(callIndexes), suffixes);
    }

    /**
     * Finds the complete CFG reachable immediately after one chain call.
     * Conditional and switch targets must stay forward in the resulting
     * range, all executable instructions in that range must be reachable, and
     * every path must terminate with RETURN.
     */
    private static LinearSuffix boundedDistinctSuffix(
            MethodNode constructor, int callIndex,
            Set<LabelNode> methodEndHandlers) {
        int startIndex = callIndex + 1;
        int instructionCount = constructor.instructions.size();
        if (startIndex >= instructionCount) {
            return null;
        }

        Map<LabelNode, Integer> labelIndexes = labelIndexes(constructor);
        boolean[] reached = new boolean[instructionCount];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        List<Integer> conditionalBranches = new ArrayList<>();
        List<Integer> gotos = new ArrayList<>();
        Integer switchIndex = null;
        pending.add(startIndex);
        int endIndex = startIndex;
        boolean reachesReturn = false;

        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            if (index < startIndex || index >= instructionCount) {
                return null;
            }
            if (reached[index]) {
                continue;
            }
            reached[index] = true;
            endIndex = Math.max(endIndex, index + 1);

            AbstractInsnNode instruction =
                    constructor.instructions.get(index);
            int opcode = instruction.getOpcode();
            if (opcode >= 0) {
                addExceptionSuccessors(
                        constructor, labelIndexes, startIndex,
                        index, pending, methodEndHandlers);
            }
            if (opcode == Opcodes.RETURN) {
                reachesReturn = true;
                continue;
            }
            if (isReturn(opcode) || opcode == Opcodes.ATHROW
                    || opcode == Opcodes.JSR || opcode == Opcodes.RET) {
                return null;
            }
            if (instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode) {
                if (switchIndex != null || !conditionalBranches.isEmpty()) {
                    return null;
                }
                int keyIndex =
                        previousExecutableIndex(constructor, index - 1);
                if (keyIndex < startIndex
                        || !hasProvenDistinctSuffixSwitchKey(
                        constructor, startIndex, keyIndex)) {
                    return null;
                }
                List<LabelNode> targets = new ArrayList<>();
                if (instruction instanceof TableSwitchInsnNode) {
                    TableSwitchInsnNode table =
                            (TableSwitchInsnNode) instruction;
                    targets.add(table.dflt);
                    targets.addAll(table.labels);
                } else {
                    LookupSwitchInsnNode lookup =
                            (LookupSwitchInsnNode) instruction;
                    targets.add(lookup.dflt);
                    targets.addAll(lookup.labels);
                }
                for (LabelNode target : targets) {
                    Integer targetIndex = labelIndexes.get(target);
                    if (targetIndex == null || targetIndex < startIndex
                            || targetIndex <= index) {
                        return null;
                    }
                    pending.add(targetIndex);
                }
                switchIndex = index;
                continue;
            }
            if (instruction instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) instruction;
                Integer targetIndex = labelIndexes.get(jump.label);
                if (targetIndex == null || targetIndex < startIndex
                        || targetIndex <= index) {
                    return null;
                }
                if (opcode == Opcodes.GOTO) {
                    gotos.add(index);
                } else if (isUnaryIntCompare(opcode)
                        || isBinaryIntCompare(opcode)) {
                    if (switchIndex != null) {
                        return null;
                    }
                    conditionalBranches.add(index);
                    if (conditionalBranches.size() > 1
                            || index + 1 >= instructionCount) {
                        return null;
                    }
                    pending.add(index + 1);
                } else {
                    return null;
                }
                pending.add(targetIndex);
                continue;
            }
            if (opcode >= 0 && !isComparableLinearInstruction(instruction)) {
                return null;
            }
            if (index + 1 >= instructionCount) {
                return null;
            }
            pending.add(index + 1);
        }

        if (!reachesReturn) {
            return null;
        }
        for (int i = startIndex; i < endIndex; i++) {
            if (constructor.instructions.get(i).getOpcode() >= 0
                    && !reached[i]) {
                return null;
            }
        }
        if (conditionalBranches.isEmpty() && switchIndex == null) {
            if (!gotos.isEmpty()) {
                return null;
            }
        } else {
            if (!conditionalBranches.isEmpty()
                    && !hasProvenDistinctSuffixCondition(
                    constructor, startIndex, conditionalBranches.get(0))) {
                return null;
            }
            for (Integer gotoIndex : gotos) {
                JumpInsnNode jump = (JumpInsnNode)
                        constructor.instructions.get(gotoIndex);
                int targetIndex = labelIndexes.get(jump.label);
                int targetExecutable =
                        firstExecutableIndex(constructor, targetIndex);
                if (targetExecutable >= endIndex
                        || constructor.instructions.get(targetExecutable)
                        .getOpcode() != Opcodes.RETURN) {
                    return null;
                }
            }
        }
        return new LinearSuffix(startIndex, endIndex);
    }

    private static void addExceptionSuccessors(
            MethodNode constructor, Map<LabelNode, Integer> labelIndexes,
            int suffixStartIndex, int instructionIndex,
            ArrayDeque<Integer> pending,
            Set<LabelNode> methodEndHandlers) {
        for (TryCatchBlockNode tryCatch : constructor.tryCatchBlocks) {
            Integer startIndex = labelIndexes.get(tryCatch.start);
            Integer endIndex = labelIndexes.get(tryCatch.end);
            Integer handlerIndex = labelIndexes.get(tryCatch.handler);
            if (startIndex != null && endIndex != null && handlerIndex != null
                    && instructionIndex >= startIndex
                    && instructionIndex < endIndex
                    // A backward handler is outside this candidate suffix.
                    // Its exact isolated-prefix form is checked after all
                    // suffix ranges have been discovered.
                    && handlerIndex >= suffixStartIndex
                    // A proven isolated method-end handler is relocated after
                    // suffix discovery; it is not a path-selected suffix.
                    && !methodEndHandlers.contains(tryCatch.handler)) {
                pending.add(handlerIndex);
            }
        }
    }

    private static boolean hasProvenDistinctSuffixSwitchKey(
            MethodNode constructor, int suffixStart, int keyIndex) {
        AbstractInsnNode key = constructor.instructions.get(keyIndex);
        if (!isDistinctSuffixIntLoad(constructor, key)) {
            return false;
        }
        int local = ((VarInsnNode) key).var;
        for (int i = suffixStart; i < keyIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof IincInsnNode
                    && ((IincInsnNode) instruction).var == local
                    || instruction.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) instruction).var == local) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasProvenDistinctSuffixCondition(
            MethodNode constructor, int suffixStart, int branchIndex) {
        AbstractInsnNode branch = constructor.instructions.get(branchIndex);
        int rightIndex =
                previousExecutableIndex(constructor, branchIndex - 1);
        if (rightIndex < suffixStart) {
            return false;
        }
        AbstractInsnNode right = constructor.instructions.get(rightIndex);
        if (isUnaryIntCompare(branch.getOpcode())) {
            return isDistinctSuffixIntLoad(constructor, right);
        }
        if (!isBinaryIntCompare(branch.getOpcode())
                || !isDistinctSuffixIntInput(constructor, right)) {
            return false;
        }
        int leftIndex =
                previousExecutableIndex(constructor, rightIndex - 1);
        return leftIndex >= suffixStart
                && isDistinctSuffixIntLoad(
                constructor, constructor.instructions.get(leftIndex));
    }

    private static boolean isDistinctSuffixIntInput(
            MethodNode constructor, AbstractInsnNode instruction) {
        return isDistinctSuffixIntLoad(constructor, instruction)
                || isIntFamilyConstant(instruction);
    }

    private static boolean isDistinctSuffixIntLoad(
            MethodNode constructor, AbstractInsnNode instruction) {
        if (instruction.getOpcode() != Opcodes.ILOAD) {
            return false;
        }
        int local = ((VarInsnNode) instruction).var;
        return isDeclaredIntArgument(constructor, local)
                || local >= firstExtraLocal(constructor);
    }

    private static int executableInstructionCount(
            MethodNode constructor, LinearSuffix suffix) {
        int count = 0;
        for (int i = suffix.startIndex; i < suffix.endIndex; i++) {
            if (constructor.instructions.get(i).getOpcode() >= 0) {
                count++;
            }
        }
        return count;
    }

    private static boolean sameBoundedSuffix(
            MethodNode constructor, LinearSuffix left, LinearSuffix right) {
        List<Integer> leftInstructions =
                executableIndexes(constructor, left);
        List<Integer> rightInstructions =
                executableIndexes(constructor, right);
        if (leftInstructions.size() != rightInstructions.size()) {
            return false;
        }
        Map<LabelNode, Integer> labelIndexes = labelIndexes(constructor);
        for (int i = 0; i < leftInstructions.size(); i++) {
            AbstractInsnNode leftInstruction =
                    constructor.instructions.get(leftInstructions.get(i));
            AbstractInsnNode rightInstruction =
                    constructor.instructions.get(rightInstructions.get(i));
            if (leftInstruction instanceof JumpInsnNode
                    || rightInstruction instanceof JumpInsnNode) {
                if (!(leftInstruction instanceof JumpInsnNode)
                        || !(rightInstruction instanceof JumpInsnNode)
                        || leftInstruction.getOpcode()
                        != rightInstruction.getOpcode()) {
                    return false;
                }
                int leftTarget = firstExecutableIndex(
                        constructor, labelIndexes.get(
                                ((JumpInsnNode) leftInstruction).label));
                int rightTarget = firstExecutableIndex(
                        constructor, labelIndexes.get(
                                ((JumpInsnNode) rightInstruction).label));
                if (leftInstructions.indexOf(leftTarget)
                        != rightInstructions.indexOf(rightTarget)) {
                    return false;
                }
            } else if (leftInstruction instanceof TableSwitchInsnNode
                    || rightInstruction instanceof TableSwitchInsnNode) {
                if (!(leftInstruction instanceof TableSwitchInsnNode)
                        || !(rightInstruction instanceof TableSwitchInsnNode)) {
                    return false;
                }
                TableSwitchInsnNode leftSwitch =
                        (TableSwitchInsnNode) leftInstruction;
                TableSwitchInsnNode rightSwitch =
                        (TableSwitchInsnNode) rightInstruction;
                if (leftSwitch.min != rightSwitch.min
                        || leftSwitch.max != rightSwitch.max
                        || !sameBoundedSwitchTargets(
                        constructor, leftInstructions, rightInstructions,
                        leftSwitch.dflt, rightSwitch.dflt,
                        leftSwitch.labels, rightSwitch.labels)) {
                    return false;
                }
            } else if (leftInstruction instanceof LookupSwitchInsnNode
                    || rightInstruction instanceof LookupSwitchInsnNode) {
                if (!(leftInstruction instanceof LookupSwitchInsnNode)
                        || !(rightInstruction instanceof LookupSwitchInsnNode)) {
                    return false;
                }
                LookupSwitchInsnNode leftSwitch =
                        (LookupSwitchInsnNode) leftInstruction;
                LookupSwitchInsnNode rightSwitch =
                        (LookupSwitchInsnNode) rightInstruction;
                if (!leftSwitch.keys.equals(rightSwitch.keys)
                        || !sameBoundedSwitchTargets(
                        constructor, leftInstructions, rightInstructions,
                        leftSwitch.dflt, rightSwitch.dflt,
                        leftSwitch.labels, rightSwitch.labels)) {
                    return false;
                }
            } else if (!sameLinearInstruction(
                    leftInstruction, rightInstruction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBoundedSwitchTargets(
            MethodNode constructor, List<Integer> leftInstructions,
            List<Integer> rightInstructions, LabelNode leftDefault,
            LabelNode rightDefault, List<LabelNode> leftLabels,
            List<LabelNode> rightLabels) {
        if (leftLabels.size() != rightLabels.size()
                || !sameBoundedSwitchTarget(
                constructor, leftInstructions, rightInstructions,
                leftDefault, rightDefault)) {
            return false;
        }
        for (int i = 0; i < leftLabels.size(); i++) {
            if (!sameBoundedSwitchTarget(
                    constructor, leftInstructions, rightInstructions,
                    leftLabels.get(i), rightLabels.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBoundedSwitchTarget(
            MethodNode constructor, List<Integer> leftInstructions,
            List<Integer> rightInstructions, LabelNode leftTarget,
            LabelNode rightTarget) {
        Map<LabelNode, Integer> indexes = labelIndexes(constructor);
        int leftExecutable = firstExecutableIndex(
                constructor, indexes.get(leftTarget));
        int rightExecutable = firstExecutableIndex(
                constructor, indexes.get(rightTarget));
        return leftInstructions.indexOf(leftExecutable)
                == rightInstructions.indexOf(rightExecutable);
    }

    private static List<Integer> executableIndexes(
            MethodNode constructor, LinearSuffix suffix) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = suffix.startIndex; i < suffix.endIndex; i++) {
            if (constructor.instructions.get(i).getOpcode() >= 0) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static LinearSuffix linearSuffix(
            MethodNode constructor, int callIndex) {
        for (int i = callIndex + 1;
             i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            int opcode = instruction.getOpcode();
            if (opcode < 0) {
                continue;
            }
            if (opcode == Opcodes.RETURN) {
                return new LinearSuffix(callIndex + 1, i + 1);
            }
            if (isReturn(opcode) || opcode == Opcodes.ATHROW
                    || opcode == Opcodes.JSR || opcode == Opcodes.RET
                    || instruction instanceof JumpInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || !isComparableLinearInstruction(instruction)) {
                return null;
            }
        }
        return null;
    }

    private static boolean sameLinearSuffix(
            MethodNode constructor, LinearSuffix left, LinearSuffix right) {
        List<Integer> leftInstructions =
                executableIndexes(constructor, left);
        List<Integer> rightInstructions =
                executableIndexes(constructor, right);
        if (leftInstructions.size() != rightInstructions.size()) {
            return false;
        }
        for (int i = 0; i < leftInstructions.size(); i++) {
            if (!sameLinearInstruction(
                    constructor.instructions.get(leftInstructions.get(i)),
                    constructor.instructions.get(rightInstructions.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isComparableLinearInstruction(
            AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode
                || instruction instanceof IntInsnNode
                || instruction instanceof VarInsnNode
                || instruction instanceof TypeInsnNode
                || instruction instanceof FieldInsnNode
                || instruction instanceof IincInsnNode) {
            return true;
        }
        if (instruction instanceof MethodInsnNode) {
            return !"<init>".equals(
                    ((MethodInsnNode) instruction).name);
        }
        if (instruction instanceof LdcInsnNode) {
            Object constant = ((LdcInsnNode) instruction).cst;
            return constant instanceof String
                    || constant instanceof Integer
                    || constant instanceof Float
                    || constant instanceof Long
                    || constant instanceof Double
                    || constant instanceof Type;
        }
        return false;
    }

    private static boolean sameLinearInstruction(
            AbstractInsnNode left, AbstractInsnNode right) {
        if (left.getOpcode() != right.getOpcode()
                || left.getClass() != right.getClass()
                || (!isComparableLinearInstruction(left)
                && left.getOpcode() != Opcodes.RETURN)) {
            return false;
        }
        if (left instanceof InsnNode) {
            return true;
        }
        if (left instanceof IntInsnNode) {
            return ((IntInsnNode) left).operand
                    == ((IntInsnNode) right).operand;
        }
        if (left instanceof VarInsnNode) {
            return ((VarInsnNode) left).var
                    == ((VarInsnNode) right).var;
        }
        if (left instanceof TypeInsnNode) {
            return ((TypeInsnNode) left).desc.equals(
                    ((TypeInsnNode) right).desc);
        }
        if (left instanceof FieldInsnNode) {
            FieldInsnNode leftField = (FieldInsnNode) left;
            FieldInsnNode rightField = (FieldInsnNode) right;
            return leftField.owner.equals(rightField.owner)
                    && leftField.name.equals(rightField.name)
                    && leftField.desc.equals(rightField.desc);
        }
        if (left instanceof MethodInsnNode) {
            MethodInsnNode leftMethod = (MethodInsnNode) left;
            MethodInsnNode rightMethod = (MethodInsnNode) right;
            return leftMethod.owner.equals(rightMethod.owner)
                    && leftMethod.name.equals(rightMethod.name)
                    && leftMethod.desc.equals(rightMethod.desc)
                    && leftMethod.itf == rightMethod.itf;
        }
        if (left instanceof IincInsnNode) {
            IincInsnNode leftIncrement = (IincInsnNode) left;
            IincInsnNode rightIncrement = (IincInsnNode) right;
            return leftIncrement.var == rightIncrement.var
                    && leftIncrement.incr == rightIncrement.incr;
        }
        if (left instanceof LdcInsnNode) {
            return ((LdcInsnNode) left).cst.equals(
                    ((LdcInsnNode) right).cst);
        }
        return false;
    }

    /**
     * The hidden bridge cannot carry an existing operand stack. This analysis
     * also proves that each selected call consumes the constructor receiver.
     */
    private static boolean hasEmptyChainEntryStacks(
            MethodNode constructor, List<Integer> callIndexes) {
        ReceiverFrame[] frames;
        try {
            frames = receiverFrames(
                    constructor, constructor.instructions.size() - 1,
                    new ArrayList<>());
        } catch (ReceiverAnalysisFailure failure) {
            return false;
        }
        for (Integer callIndex : callIndexes) {
            ReceiverFrame frame = frames[callIndex];
            MethodInsnNode call =
                    (MethodInsnNode) constructor.instructions.get(callIndex);
            int argumentValues = Type.getArgumentTypes(call.desc).length;
            if (Type.getReturnType(call.desc).getSort() != Type.VOID
                    || frame == null
                    || frame.stack.size() != argumentValues + 1
                    || !frame.stack.get(0).receiver) {
                return false;
            }
        }
        return true;
    }

    /**
     * Restricts bounded multi-call forms to calls whose complete operand
     * sequence is visible locally: a direct receiver ALOAD followed by direct
     * declared-argument loads, one AALOAD from an unchanged declared array
     * argument or its proven prefix extra-local copy at a constant or
     * single-load proven int index, one GETFIELD on an unchanged declared
     * object argument or its proven prefix extra-local copy, proven prefix
     * copies of declared primitive loads, bounded primitive computations, or
     * int-family constants. The identical-copy and distinct-suffix forms may
     * accept a direct alias load only when their separate receiver-frame proof
     * succeeds.
     */
    private static boolean hasDirectDeclaredChainInputs(
            MethodNode constructor, List<Integer> callIndexes,
            boolean allowReceiverAlias) {
        Map<Integer, Type> declaredArguments = new HashMap<>();
        int declaredLocal = 1;
        for (Type argument : Type.getArgumentTypes(constructor.desc)) {
            declaredArguments.put(declaredLocal, argument);
            declaredLocal += argument.getSize();
        }
        Map<Integer, Integer> prefixArrayCopies =
                provenPrefixArrayCopyLocals(
                        constructor, callIndexes, declaredArguments);
        Map<Integer, Integer> prefixObjectCopies =
                provenPrefixObjectCopyLocals(
                        constructor, callIndexes, declaredArguments);
        Set<Integer> prefixIntCopies =
                provenPrefixIntCopyLocals(
                        constructor, callIndexes, declaredArguments);
        Set<Integer> prefixLongCopies =
                provenPrefixLongCopyLocals(
                        constructor, callIndexes, declaredArguments);
        Set<Integer> prefixFloatCopies =
                provenPrefixFloatCopyLocals(
                        constructor, callIndexes, declaredArguments);
        Set<Integer> prefixDoubleCopies =
                provenPrefixDoubleCopyLocals(
                        constructor, callIndexes, declaredArguments);

        for (Integer callIndex : callIndexes) {
            MethodInsnNode call =
                    (MethodInsnNode) constructor.instructions.get(callIndex);
            Type[] callArguments = Type.getArgumentTypes(call.desc);
            int inputIndex =
                    previousExecutableIndex(constructor, callIndex - 1);
            for (int i = callArguments.length - 1; i >= 0; i--) {
                Integer previousInput = previousProvenChainInput(
                        constructor, inputIndex, callArguments[i],
                        declaredArguments, prefixArrayCopies,
                        prefixObjectCopies,
                        prefixIntCopies, prefixLongCopies, prefixFloatCopies,
                        prefixDoubleCopies);
                if (previousInput == null) {
                    return false;
                }
                inputIndex = previousInput;
            }
            if (inputIndex < 0
                    || constructor.instructions.get(inputIndex).getOpcode()
                    != Opcodes.ALOAD
                    || !allowReceiverAlias
                    && ((VarInsnNode) constructor.instructions.get(inputIndex)).var
                    != 0) {
                return false;
            }
        }
        return true;
    }

    private static Integer previousProvenChainInput(
            MethodNode constructor, int inputIndex, Type expected,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Map<Integer, Integer> prefixObjectCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixLongCopies,
            Set<Integer> prefixFloatCopies,
            Set<Integer> prefixDoubleCopies) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isDirectDeclaredArgumentLoad(
                input, expected, declaredArguments)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        Integer beforeNew = previousProvenNewChainInput(
                constructor, inputIndex, expected, declaredArguments,
                prefixArrayCopies, prefixIntCopies, prefixLongCopies);
        if (beforeNew != null) {
            return beforeNew;
        }
        Integer beforeGetfield = previousProvenGetfieldChainInput(
                constructor, inputIndex, expected, declaredArguments,
                prefixObjectCopies);
        if (beforeGetfield != null) {
            return beforeGetfield;
        }
        if (expected.getSort() == Type.OBJECT
                || expected.getSort() == Type.ARRAY) {
            return previousProvenReferenceChainInput(
                    constructor, inputIndex, expected, declaredArguments,
                    prefixArrayCopies, prefixIntCopies);
        }
        if (expected.getSort() == Type.LONG) {
            return previousProvenLongChainOperand(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixLongCopies,
                    MAX_PROVEN_LONG_CHAIN_BINARY_LEVELS);
        }
        if (expected.getSort() == Type.FLOAT) {
            return previousProvenFloatChainOperand(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixFloatCopies,
                    MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS);
        }
        if (expected.getSort() == Type.DOUBLE) {
            return previousProvenDoubleChainOperand(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixDoubleCopies,
                    MAX_PROVEN_DOUBLE_CHAIN_BINARY_LEVELS);
        }
        if (!isIntFamily(expected)) {
            return null;
        }
        return previousProvenIntChainOperand(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies,
                MAX_PROVEN_INT_CHAIN_BINARY_LEVELS);
    }

    /**
     * Proves an isolated retained-prefix allocation with zero through six
     * single-instruction proven int-family or long initializer leaves. The
     * allocated reference descriptor must exactly match the chain argument
     * descriptor. Float, double, and computed initializer inputs fail closed.
     */
    private static Integer previousProvenNewChainInput(
            MethodNode constructor, int inputIndex, Type expected,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixLongCopies) {
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (!(input instanceof MethodInsnNode)
                || input.getOpcode() != Opcodes.INVOKESPECIAL) {
            return null;
        }
        MethodInsnNode initializer = (MethodInsnNode) input;
        Type[] initializerArguments =
                Type.getArgumentTypes(initializer.desc);
        if (!"<init>".equals(initializer.name)
                || !Type.VOID_TYPE.equals(
                Type.getReturnType(initializer.desc))
                || initializerArguments.length > 6
                || initializer.itf) {
            return null;
        }
        int duplicateIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        for (int i = initializerArguments.length - 1; i >= 0; i--) {
            if (duplicateIndex < 0) {
                return null;
            }
            int beforeSingleArgument =
                    previousExecutableIndex(
                            constructor, duplicateIndex - 1);
            Integer beforeArgument;
            if (isIntFamily(initializerArguments[i])) {
                beforeArgument = previousProvenIntChainLeaf(
                        constructor, duplicateIndex, declaredArguments,
                        prefixArrayCopies, prefixIntCopies);
            } else if (initializerArguments[i].getSort() == Type.LONG) {
                beforeArgument = previousProvenLongChainLeaf(
                        constructor, duplicateIndex, declaredArguments,
                        prefixArrayCopies, prefixIntCopies, prefixLongCopies);
            } else {
                return null;
            }
            if (beforeArgument == null
                    || beforeArgument != beforeSingleArgument) {
                return null;
            }
            duplicateIndex = beforeArgument;
        }
        if (duplicateIndex < 0
                || constructor.instructions.get(duplicateIndex).getOpcode()
                != Opcodes.DUP) {
            return null;
        }
        int allocationIndex =
                previousExecutableIndex(constructor, duplicateIndex - 1);
        if (allocationIndex < 0) {
            return null;
        }
        AbstractInsnNode allocation =
                constructor.instructions.get(allocationIndex);
        if (!(allocation instanceof TypeInsnNode)
                || allocation.getOpcode() != Opcodes.NEW
                || !initializer.owner.equals(
                ((TypeInsnNode) allocation).desc)
                || !sameInvocationCarrier(
                Type.getObjectType(initializer.owner), expected)) {
            return null;
        }
        return previousExecutableIndex(constructor, allocationIndex - 1);
    }

    /**
     * Proves the isolated retained-prefix field read
     * {@code ALOAD receiver; GETFIELD}. The receiver must be an unchanged
     * declared object argument or its proven prefix extra-local copy, and that
     * declared argument's exact class must own the field. Exact ownership
     * deliberately avoids guessing about class hierarchies that are
     * unavailable to this local proof. The field and its receiver load remain
     * JVM bytecode, preserving JVM null checks and field access semantics.
     */
    private static Integer previousProvenGetfieldChainInput(
            MethodNode constructor, int inputIndex, Type expected,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixObjectCopies) {
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (!(input instanceof FieldInsnNode)
                || input.getOpcode() != Opcodes.GETFIELD) {
            return null;
        }
        FieldInsnNode field = (FieldInsnNode) input;
        int receiverIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (receiverIndex < 0) {
            return null;
        }
        AbstractInsnNode receiver =
                constructor.instructions.get(receiverIndex);
        if (!(receiver instanceof VarInsnNode)
                || receiver.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        int receiverLocal = ((VarInsnNode) receiver).var;
        int declaredSourceLocal = receiverLocal;
        Type declaredReceiver = declaredArguments.get(declaredSourceLocal);
        if (declaredReceiver == null) {
            Integer copiedFrom = prefixObjectCopies.get(receiverLocal);
            if (copiedFrom == null) {
                return null;
            }
            declaredSourceLocal = copiedFrom;
            declaredReceiver = declaredArguments.get(declaredSourceLocal);
        }
        if (receiverLocal == 0
                || declaredSourceLocal == 0
                || declaredReceiver == null
                || declaredReceiver.getSort() != Type.OBJECT
                || !declaredReceiver.getInternalName().equals(field.owner)) {
            return null;
        }
        Type fieldType;
        try {
            fieldType = Type.getType(field.desc);
        } catch (IllegalArgumentException malformedDescriptor) {
            return null;
        }
        if (!sameInvocationCarrier(fieldType, expected)) {
            return null;
        }
        for (int i = 0; i < inputIndex; i++) {
            AbstractInsnNode instruction =
                    constructor.instructions.get(i);
            Type stored = storeType(instruction);
            if (stored != null) {
                int storedLocal = ((VarInsnNode) instruction).var;
                if (localRangesOverlap(
                        storedLocal, stored,
                        declaredSourceLocal, declaredReceiver)) {
                    return null;
                }
                if (receiverLocal != declaredSourceLocal
                        && localRangesOverlap(
                        storedLocal, stored, receiverLocal,
                        Type.getType(Object.class))) {
                    int copySourceIndex =
                            previousExecutableIndex(constructor, i - 1);
                    if (instruction.getOpcode() != Opcodes.ASTORE
                            || storedLocal != receiverLocal
                            || copySourceIndex < 0
                            || constructor.instructions.get(copySourceIndex)
                            .getOpcode() != Opcodes.ALOAD
                            || ((VarInsnNode) constructor.instructions.get(
                            copySourceIndex)).var != declaredSourceLocal) {
                        return null;
                    }
                }
            } else if (instruction instanceof IincInsnNode
                    && (localRangesOverlap(
                    ((IincInsnNode) instruction).var, Type.INT_TYPE,
                    declaredSourceLocal, declaredReceiver)
                    || receiverLocal != declaredSourceLocal
                    && localRangesOverlap(
                    ((IincInsnNode) instruction).var, Type.INT_TYPE,
                    receiverLocal, Type.getType(Object.class)))) {
                return null;
            }
        }
        return previousExecutableIndex(constructor, receiverIndex - 1);
    }

    /**
     * Proves the exact retained-prefix reference computation
     * {@code ALOAD array; index; AALOAD}. The index must be a constant or one
     * single-instruction declared/proven-copy ILOAD. The loaded array must be
     * either an unchanged declared constructor argument or its proven prefix
     * extra-local copy. The declared source local must remain unchanged, and
     * no earlier array store is accepted. The load remains JVM bytecode,
     * preserving null, bounds, and reference-array semantics without
     * reproducing them in native code.
     */
    private static Integer previousProvenReferenceChainInput(
            MethodNode constructor, int inputIndex, Type expected,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies) {
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (input.getOpcode() != Opcodes.AALOAD) {
            return null;
        }
        int indexIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (indexIndex < 0) {
            return null;
        }
        int beforeSingleIndex =
                previousExecutableIndex(constructor, indexIndex - 1);
        if (!isIntFamilyConstant(
                constructor.instructions.get(indexIndex))) {
            Integer beforeIndex = previousProvenIntChainLeaf(
                    constructor, indexIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies);
            if (beforeIndex == null
                    || beforeIndex != beforeSingleIndex) {
                return null;
            }
        }
        int arrayIndex = beforeSingleIndex;
        if (arrayIndex < 0) {
            return null;
        }
        AbstractInsnNode array = constructor.instructions.get(arrayIndex);
        if (!(array instanceof VarInsnNode)
                || array.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        int loadedArrayLocal = ((VarInsnNode) array).var;
        Type declaredArray = declaredArguments.get(loadedArrayLocal);
        int declaredArrayLocal = loadedArrayLocal;
        if (declaredArray == null) {
            Integer copiedFrom =
                    prefixArrayCopies.get(loadedArrayLocal);
            if (copiedFrom == null) {
                return null;
            }
            declaredArrayLocal = copiedFrom;
            declaredArray = declaredArguments.get(declaredArrayLocal);
        }
        if (!isReferenceArray(declaredArray)) {
            return null;
        }
        Type component = Type.getType(
                declaredArray.getDescriptor().substring(1));
        if (!sameInvocationCarrier(component, expected)) {
            return null;
        }
        for (int i = 0; i < inputIndex; i++) {
            AbstractInsnNode instruction =
                    constructor.instructions.get(i);
            Type stored = storeType(instruction);
            if (i < arrayIndex && stored != null
                    && localRangesOverlap(
                    ((VarInsnNode) instruction).var, stored,
                    declaredArrayLocal, declaredArray)) {
                return null;
            }
            if (isArrayStoreOpcode(instruction.getOpcode())) {
                return null;
            }
        }
        return previousExecutableIndex(constructor, arrayIndex - 1);
    }

    /**
     * Finds extra array locals whose only overlapping write before the
     * final chain call is one pre-first-call ASTORE directly fed by an ALOAD of
     * a declared reference-array or supported primitive-array argument.
     * Requiring the
     * resulting reference state at every chain call proves that the store
     * dominates all selected paths. The declared source local is retained so
     * each array-load proof can independently require that argument to remain
     * unchanged and enforce the exact source-array type it consumes.
     */
    private static Map<Integer, Integer> provenPrefixArrayCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Map<Integer, Integer> proven = new HashMap<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        Map<Integer, Integer> candidateSources = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.ASTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal || sourceIndex < 0) {
                continue;
            }
            AbstractInsnNode source =
                    constructor.instructions.get(sourceIndex);
            if (!(source instanceof VarInsnNode)
                    || source.getOpcode() != Opcodes.ALOAD) {
                continue;
            }
            int sourceLocal = ((VarInsnNode) source).var;
            Type declaredArray = declaredArguments.get(sourceLocal);
            if (!isReferenceArray(declaredArray)
                    && !isIntFamilyLoadArray(declaredArray)
                    && !isWideLoadArray(declaredArray)) {
                continue;
            }
            candidateStores.put(local, i);
            candidateSources.put(local, sourceLocal);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.getType(Object.class))
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.getType(Object.class))) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_REFERENCE) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.put(local, candidateSources.get(local));
            }
        }
        return proven;
    }

    /**
     * Finds extra object locals whose only overlapping write before the final
     * chain call is one pre-first-call ASTORE directly fed by an ALOAD of a
     * declared object argument other than local 0. Requiring reference state
     * at every chain call proves that the store dominates every selected path.
     * The declared source local is retained so each GETFIELD proof can require
     * that source to remain unchanged and enforce its exact owner class.
     */
    private static Map<Integer, Integer> provenPrefixObjectCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Map<Integer, Integer> proven = new HashMap<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        Map<Integer, Integer> candidateSources = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.ASTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal || sourceIndex < 0) {
                continue;
            }
            AbstractInsnNode source =
                    constructor.instructions.get(sourceIndex);
            if (!(source instanceof VarInsnNode)
                    || source.getOpcode() != Opcodes.ALOAD) {
                continue;
            }
            int sourceLocal = ((VarInsnNode) source).var;
            Type declaredObject = declaredArguments.get(sourceLocal);
            if (sourceLocal == 0
                    || declaredObject == null
                    || declaredObject.getSort() != Type.OBJECT) {
                continue;
            }
            candidateStores.put(local, i);
            candidateSources.put(local, sourceLocal);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.getType(Object.class))
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.getType(Object.class))) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_REFERENCE) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.put(local, candidateSources.get(local));
            }
        }
        return proven;
    }

    private static boolean isReferenceArray(Type type) {
        if (type == null || type.getSort() != Type.ARRAY) {
            return false;
        }
        Type component = Type.getType(type.getDescriptor().substring(1));
        if (component.getSort() == Type.OBJECT) {
            return true;
        }
        if (component.getSort() != Type.ARRAY) {
            return false;
        }
        return component.getElementType().getSort() == Type.OBJECT;
    }

    private static boolean isIntFamilyLoadArray(Type type) {
        return Type.getType("[I").equals(type)
                || Type.getType("[B").equals(type)
                || Type.getType("[Z").equals(type)
                || Type.getType("[C").equals(type)
                || Type.getType("[S").equals(type);
    }

    private static boolean isWideLoadArray(Type type) {
        return Type.getType("[J").equals(type)
                || Type.getType("[F").equals(type)
                || Type.getType("[D").equals(type);
    }

    private static boolean isArrayStoreOpcode(int opcode) {
        return opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE;
    }

    /**
     * Proves a long operand with a bounded number of binary levels. Long shifts
     * consume a single-instruction int-family count leaf after their
     * recursively proven long value; the other admitted binaries recursively
     * prove both long operands.
     * Every binary descent consumes one level from the separate eight-level
     * long budget, so nine-or-more nested long binaries remain rejected.
     */
    private static Integer previousProvenLongChainOperand(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixLongCopies,
            int remainingBinaryLevels) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        int opcode = input.getOpcode();
        boolean longShift = opcode == Opcodes.LSHL
                || opcode == Opcodes.LSHR
                || opcode == Opcodes.LUSHR;
        boolean longBinary = opcode == Opcodes.LADD
                || opcode == Opcodes.LSUB
                || opcode == Opcodes.LMUL
                || opcode == Opcodes.LDIV
                || opcode == Opcodes.LREM
                || opcode == Opcodes.LAND
                || opcode == Opcodes.LOR
                || opcode == Opcodes.LXOR
                || longShift;
        if (!longBinary) {
            return previousProvenLongChainLeaf(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixLongCopies);
        }
        if (remainingBinaryLevels == 0) {
            return null;
        }
        if (longShift) {
            int countIndex =
                    previousExecutableIndex(constructor, inputIndex - 1);
            int beforeSingleCount =
                    previousExecutableIndex(constructor, countIndex - 1);
            Integer beforeCount = previousProvenIntChainLeaf(
                    constructor, countIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies);
            if (beforeCount == null
                    || beforeCount != beforeSingleCount) {
                return null;
            }
            return previousProvenLongChainOperand(
                    constructor, beforeCount,
                    declaredArguments, prefixArrayCopies,
                    prefixIntCopies, prefixLongCopies,
                    remainingBinaryLevels - 1);
        }
        Integer beforeRight = previousProvenLongChainOperand(
                constructor,
                previousExecutableIndex(constructor, inputIndex - 1),
                declaredArguments, prefixArrayCopies,
                prefixIntCopies, prefixLongCopies,
                remainingBinaryLevels - 1);
        if (beforeRight == null) {
            return null;
        }
        return previousProvenLongChainOperand(
                constructor, beforeRight, declaredArguments,
                prefixArrayCopies, prefixIntCopies, prefixLongCopies,
                remainingBinaryLevels - 1);
    }

    /**
     * Proves one non-recursive long leaf: a declared LLOAD, one proven prefix
     * copy of a declared LLOAD, LCONST_0/1, an LDC whose constant is a Long,
     * one constant- or single-load-indexed LALOAD from an unchanged declared
     * long array or its proven prefix extra-local copy, or one LNEG over a
     * direct declared LLOAD or its proven prefix copy.
     */
    private static Integer previousProvenLongChainLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixLongCopies) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isDirectDeclaredArgumentLoad(
                input, Type.LONG_TYPE, declaredArguments)
                || isLongConstant(input)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        if (input.getOpcode() == Opcodes.LLOAD
                && !isDirectDeclaredArgumentLoad(
                input, Type.LONG_TYPE, declaredArguments)
                && prefixLongCopies.contains(
                ((VarInsnNode) input).var)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        Integer beforeArrayLoad = previousProvenDeclaredArrayLoadLeaf(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies,
                Opcodes.LALOAD, Type.getType("[J"));
        if (beforeArrayLoad != null) {
            return beforeArrayLoad;
        }
        if (input.getOpcode() != Opcodes.LNEG) {
            return null;
        }
        int operandIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (operandIndex < 0) {
            return null;
        }
        AbstractInsnNode operand =
                constructor.instructions.get(operandIndex);
        if (!isDirectDeclaredArgumentLoad(
                operand, Type.LONG_TYPE, declaredArguments)
                && (operand.getOpcode() != Opcodes.LLOAD
                || !prefixLongCopies.contains(
                ((VarInsnNode) operand).var))) {
            return null;
        }
        return previousExecutableIndex(constructor, operandIndex - 1);
    }

    /**
     * Finds extra long locals whose only overlapping write before the final
     * chain call is one pre-first-call LSTORE directly fed by a declared
     * LLOAD. Requiring the resulting long state at every chain call proves
     * that the store dominates all selected paths. The scan deliberately
     * stops at the final chain call; suffix extra-local forwarding is a
     * separate proof.
     */
    private static Set<Integer> provenPrefixLongCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Set<Integer> proven = new HashSet<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.LSTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal
                    || sourceIndex < 0
                    || !isDirectDeclaredArgumentLoad(
                    constructor.instructions.get(sourceIndex),
                    Type.LONG_TYPE, declaredArguments)) {
                continue;
            }
            candidateStores.put(local, i);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.LONG_TYPE)
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.LONG_TYPE)) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_LONG) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.add(local);
            }
        }
        return proven;
    }

    private static boolean isLongConstant(AbstractInsnNode input) {
        int opcode = input.getOpcode();
        return opcode == Opcodes.LCONST_0
                || opcode == Opcodes.LCONST_1
                || opcode == Opcodes.LDC
                && input instanceof LdcInsnNode
                && ((LdcInsnNode) input).cst instanceof Long;
    }

    /**
     * Proves a float operand with at most eight FADD, FSUB, FMUL, FDIV, or FREM
     * levels. The separate eight-level budget keeps nine-or-more nested float
     * binaries fail-closed; an admitted FNEG leaf does not consume that binary
     * budget.
     */
    private static Integer previousProvenFloatChainOperand(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixFloatCopies,
            int remainingBinaryLevels) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        int opcode = input.getOpcode();
        if (opcode != Opcodes.FADD
                && opcode != Opcodes.FSUB
                && opcode != Opcodes.FMUL
                && opcode != Opcodes.FDIV
                && opcode != Opcodes.FREM) {
            return previousProvenFloatChainLeaf(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixFloatCopies);
        }
        if (remainingBinaryLevels == 0) {
            return null;
        }
        Integer beforeRight = previousProvenFloatChainOperand(
                constructor,
                previousExecutableIndex(constructor, inputIndex - 1),
                declaredArguments, prefixArrayCopies,
                prefixIntCopies, prefixFloatCopies,
                remainingBinaryLevels - 1);
        if (beforeRight == null) {
            return null;
        }
        return previousProvenFloatChainOperand(
                constructor, beforeRight, declaredArguments,
                prefixArrayCopies, prefixIntCopies, prefixFloatCopies,
                remainingBinaryLevels - 1);
    }

    /**
     * Proves one float leaf: a declared FLOAD, one proven prefix copy of a
     * declared FLOAD, FCONST_0/1/2, an LDC whose constant is a Float, one
     * constant- or single-load-indexed FALOAD from an unchanged declared float
     * array or its proven prefix extra-local copy, or one FNEG over a direct
     * declared FLOAD or its proven prefix copy.
     */
    private static Integer previousProvenFloatChainLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixFloatCopies) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isDirectDeclaredArgumentLoad(
                input, Type.FLOAT_TYPE, declaredArguments)
                || isFloatConstant(input)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        if (input.getOpcode() == Opcodes.FLOAD
                && !isDirectDeclaredArgumentLoad(
                input, Type.FLOAT_TYPE, declaredArguments)
                && prefixFloatCopies.contains(
                ((VarInsnNode) input).var)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        Integer beforeArrayLoad = previousProvenDeclaredArrayLoadLeaf(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies,
                Opcodes.FALOAD, Type.getType("[F"));
        if (beforeArrayLoad != null) {
            return beforeArrayLoad;
        }
        if (input.getOpcode() != Opcodes.FNEG) {
            return null;
        }
        int operandIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (operandIndex < 0) {
            return null;
        }
        AbstractInsnNode operand =
                constructor.instructions.get(operandIndex);
        if (!isDirectDeclaredArgumentLoad(
                operand, Type.FLOAT_TYPE, declaredArguments)
                && (operand.getOpcode() != Opcodes.FLOAD
                || !prefixFloatCopies.contains(
                ((VarInsnNode) operand).var))) {
            return null;
        }
        return previousExecutableIndex(constructor, operandIndex - 1);
    }

    /**
     * Finds extra float locals whose only write before the final chain call is
     * one pre-first-call FSTORE directly fed by a declared FLOAD. Requiring the
     * resulting float state at every chain call proves that the store
     * dominates all selected paths. The scan deliberately stops at the final
     * chain call; suffix extra-local forwarding is a separate proof.
     */
    private static Set<Integer> provenPrefixFloatCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Set<Integer> proven = new HashSet<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.FSTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal
                    || sourceIndex < 0
                    || !isDirectDeclaredArgumentLoad(
                    constructor.instructions.get(sourceIndex),
                    Type.FLOAT_TYPE, declaredArguments)) {
                continue;
            }
            candidateStores.put(local, i);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.FLOAT_TYPE)
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.FLOAT_TYPE)) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_FLOAT) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.add(local);
            }
        }
        return proven;
    }

    private static boolean isFloatConstant(AbstractInsnNode input) {
        int opcode = input.getOpcode();
        return opcode == Opcodes.FCONST_0
                || opcode == Opcodes.FCONST_1
                || opcode == Opcodes.FCONST_2
                || opcode == Opcodes.LDC
                && input instanceof LdcInsnNode
                && ((LdcInsnNode) input).cst instanceof Float;
    }

    /**
     * Proves a double operand with at most eight DADD, DSUB, DMUL, DDIV, or
     * DREM levels. Nine-or-more nested binary levels remain rejected.
     */
    private static Integer previousProvenDoubleChainOperand(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixDoubleCopies,
            int remainingBinaryLevels) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        int opcode = input.getOpcode();
        if (opcode != Opcodes.DADD
                && opcode != Opcodes.DSUB
                && opcode != Opcodes.DMUL
                && opcode != Opcodes.DDIV
                && opcode != Opcodes.DREM) {
            return previousProvenDoubleChainLeaf(
                    constructor, inputIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies, prefixDoubleCopies);
        }
        if (remainingBinaryLevels == 0) {
            return null;
        }
        Integer beforeRight = previousProvenDoubleChainOperand(
                constructor,
                previousExecutableIndex(constructor, inputIndex - 1),
                declaredArguments, prefixArrayCopies,
                prefixIntCopies, prefixDoubleCopies,
                remainingBinaryLevels - 1);
        if (beforeRight == null) {
            return null;
        }
        return previousProvenDoubleChainOperand(
                constructor, beforeRight, declaredArguments,
                prefixArrayCopies, prefixIntCopies, prefixDoubleCopies,
                remainingBinaryLevels - 1);
    }

    /**
     * Proves one double leaf: a declared DLOAD, one proven prefix copy of a
     * declared DLOAD, DCONST_0/1, an LDC whose constant is a Double, one
     * constant- or single-load-indexed DALOAD from an unchanged declared
     * double array or its proven prefix extra-local copy, or one DNEG over a
     * direct declared DLOAD or its proven prefix copy.
     */
    private static Integer previousProvenDoubleChainLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            Set<Integer> prefixDoubleCopies) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isDirectDeclaredArgumentLoad(
                input, Type.DOUBLE_TYPE, declaredArguments)
                || isDoubleConstant(input)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        if (input.getOpcode() == Opcodes.DLOAD
                && !isDirectDeclaredArgumentLoad(
                input, Type.DOUBLE_TYPE, declaredArguments)
                && prefixDoubleCopies.contains(
                ((VarInsnNode) input).var)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        Integer beforeArrayLoad = previousProvenDeclaredArrayLoadLeaf(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies,
                Opcodes.DALOAD, Type.getType("[D"));
        if (beforeArrayLoad != null) {
            return beforeArrayLoad;
        }
        if (input.getOpcode() != Opcodes.DNEG) {
            return null;
        }
        int operandIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (operandIndex < 0) {
            return null;
        }
        AbstractInsnNode operand =
                constructor.instructions.get(operandIndex);
        if (!isDirectDeclaredArgumentLoad(
                operand, Type.DOUBLE_TYPE, declaredArguments)
                && (operand.getOpcode() != Opcodes.DLOAD
                || !prefixDoubleCopies.contains(
                ((VarInsnNode) operand).var))) {
            return null;
        }
        return previousExecutableIndex(constructor, operandIndex - 1);
    }

    /**
     * Proves the exact retained-prefix primitive computation
     * {@code ALOAD array; index; xALOAD}. The source must be an unchanged,
     * exactly typed declared constructor argument or its proven prefix
     * extra-local copy. The index must be a constant or one single-instruction
     * declared/proven-copy ILOAD. No preceding array store is accepted. The
     * complete load remains JVM bytecode, preserving null, bounds, and
     * category-two value behavior without reproducing it in native code.
     */
    private static Integer previousProvenDeclaredArrayLoadLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            int loadOpcode, Type requiredArrayType) {
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (input.getOpcode() != loadOpcode) {
            return null;
        }
        int indexIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (indexIndex < 0) {
            return null;
        }
        int beforeSingleIndex =
                previousExecutableIndex(constructor, indexIndex - 1);
        AbstractInsnNode index = constructor.instructions.get(indexIndex);
        if (!isIntFamilyConstant(index)) {
            if (index.getOpcode() != Opcodes.ILOAD) {
                return null;
            }
            Integer beforeIndex = previousProvenIntChainLeaf(
                    constructor, indexIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies);
            if (beforeIndex == null
                    || beforeIndex != beforeSingleIndex) {
                return null;
            }
        }
        int arrayIndex = beforeSingleIndex;
        if (arrayIndex < 0) {
            return null;
        }
        AbstractInsnNode array = constructor.instructions.get(arrayIndex);
        if (!(array instanceof VarInsnNode)
                || array.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        int loadedArrayLocal = ((VarInsnNode) array).var;
        int declaredArrayLocal = loadedArrayLocal;
        Type declaredArray = declaredArguments.get(loadedArrayLocal);
        if (declaredArray == null) {
            Integer copiedFrom = prefixArrayCopies.get(loadedArrayLocal);
            if (copiedFrom == null) {
                return null;
            }
            declaredArrayLocal = copiedFrom;
            declaredArray = declaredArguments.get(declaredArrayLocal);
        }
        if (!requiredArrayType.equals(declaredArray)) {
            return null;
        }
        for (int i = 0; i < inputIndex; i++) {
            AbstractInsnNode instruction =
                    constructor.instructions.get(i);
            Type stored = storeType(instruction);
            if (i < arrayIndex && stored != null
                    && localRangesOverlap(
                    ((VarInsnNode) instruction).var, stored,
                    declaredArrayLocal, declaredArray)) {
                return null;
            }
            if (isArrayStoreOpcode(instruction.getOpcode())) {
                return null;
            }
        }
        return previousExecutableIndex(constructor, arrayIndex - 1);
    }

    /**
     * Finds extra double locals whose only write before the final chain call is
     * one pre-first-call DSTORE directly fed by a declared DLOAD. Requiring the
     * resulting double state at every chain call proves that the store
     * dominates all selected paths. The scan deliberately stops at the final
     * chain call; suffix extra-local forwarding is a separate proof.
     */
    private static Set<Integer> provenPrefixDoubleCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Set<Integer> proven = new HashSet<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.DSTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal
                    || sourceIndex < 0
                    || !isDirectDeclaredArgumentLoad(
                    constructor.instructions.get(sourceIndex),
                    Type.DOUBLE_TYPE, declaredArguments)) {
                continue;
            }
            candidateStores.put(local, i);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.DOUBLE_TYPE)
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.DOUBLE_TYPE)) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_DOUBLE) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.add(local);
            }
        }
        return proven;
    }

    private static boolean isDoubleConstant(AbstractInsnNode input) {
        int opcode = input.getOpcode();
        return opcode == Opcodes.DCONST_0
                || opcode == Opcodes.DCONST_1
                || opcode == Opcodes.LDC
                && input instanceof LdcInsnNode
                && ((LdcInsnNode) input).cst instanceof Double;
    }

    /**
     * Finds extra int-family locals whose only overlapping write before the
     * final chain call is one pre-first-call ISTORE directly fed by a declared
     * int-family ILOAD. Requiring the resulting int state at every chain call
     * proves that the store dominates all selected paths. The scan deliberately
     * stops at the final chain call; suffix extra-local forwarding is a
     * separate proof.
     */
    private static Set<Integer> provenPrefixIntCopyLocals(
            MethodNode constructor, List<Integer> callIndexes,
            Map<Integer, Type> declaredArguments) {
        Set<Integer> proven = new HashSet<>();
        if (callIndexes.isEmpty()) {
            return proven;
        }
        int firstCallIndex = callIndexes.get(0);
        int lastCallIndex = callIndexes.get(callIndexes.size() - 1);
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Integer> candidateStores = new HashMap<>();
        for (int i = 0; i < firstCallIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() != Opcodes.ISTORE) {
                continue;
            }
            int local = ((VarInsnNode) instruction).var;
            int sourceIndex = previousExecutableIndex(constructor, i - 1);
            if (local < firstExtraLocal
                    || sourceIndex < 0
                    || !isDirectDeclaredIntArgumentLoad(
                    constructor.instructions.get(sourceIndex),
                    declaredArguments)) {
                continue;
            }
            candidateStores.put(local, i);
        }

        for (Map.Entry<Integer, Integer> candidate :
                candidateStores.entrySet()) {
            int local = candidate.getKey();
            int writeCount = 0;
            int writeIndex = -1;
            for (int i = 0; i < lastCallIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                Type stored = storeType(instruction);
                if (stored != null
                        && localRangesOverlap(
                        ((VarInsnNode) instruction).var, stored,
                        local, Type.INT_TYPE)
                        || instruction instanceof IincInsnNode
                        && localRangesOverlap(
                        ((IincInsnNode) instruction).var,
                        Type.INT_TYPE, local, Type.INT_TYPE)) {
                    writeCount++;
                    writeIndex = i;
                }
            }
            if (writeCount != 1 || writeIndex != candidate.getValue()) {
                continue;
            }

            int[] states = localStatesToSplit(
                    constructor, lastCallIndex, local);
            boolean dominatesCalls = true;
            for (Integer callIndex : callIndexes) {
                if (states[callIndex] != LOCAL_INT) {
                    dominatesCalls = false;
                    break;
                }
            }
            if (dominatesCalls) {
                proven.add(local);
            }
        }
        return proven;
    }

    /**
     * Proves an int-family operand with a bounded number of binary levels.
     * Every recursive descent consumes one level, so deeper trees stay rejected
     * rather than opening the local input proof without a bound. Trapping
     * IDIV/IREM nodes remain in the retained bytecode prefix.
     */
    private static Integer previousProvenIntChainOperand(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies,
            int remainingBinaryLevels) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isProvenIntChainBinary(input.getOpcode())) {
            if (remainingBinaryLevels == 0) {
                return null;
            }
            Integer beforeRight = previousProvenIntChainOperand(
                    constructor,
                    previousExecutableIndex(constructor, inputIndex - 1),
                    declaredArguments, prefixArrayCopies, prefixIntCopies,
                    remainingBinaryLevels - 1);
            if (beforeRight == null) {
                return null;
            }
            return previousProvenIntChainOperand(
                    constructor, beforeRight, declaredArguments,
                    prefixArrayCopies, prefixIntCopies,
                    remainingBinaryLevels - 1);
        }
        return previousProvenIntChainLeaf(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies);
    }

    /**
     * Proves one non-recursive int-family leaf: a declared load, one proven
     * prefix copy of a declared load, a constant, an exact constant-indexed
     * load from an unchanged declared int array or its proven prefix
     * extra-local copy, or one INEG over a direct declared load or its proven
     * prefix copy.
     */
    private static Integer previousProvenIntChainLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies) {
        if (inputIndex < 0) {
            return null;
        }
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        if (isDirectDeclaredIntArgumentLoad(input, declaredArguments)
                || isIntFamilyConstant(input)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        if (input.getOpcode() == Opcodes.ILOAD
                && !isDirectDeclaredIntArgumentLoad(
                input, declaredArguments)
                && prefixIntCopies.contains(
                ((VarInsnNode) input).var)) {
            return previousExecutableIndex(constructor, inputIndex - 1);
        }
        Integer beforeIntArrayLoad = previousProvenIntArrayLoadLeaf(
                constructor, inputIndex, declaredArguments,
                prefixArrayCopies, prefixIntCopies);
        if (beforeIntArrayLoad != null) {
            return beforeIntArrayLoad;
        }
        if (input.getOpcode() != Opcodes.INEG) {
            return null;
        }
        int operandIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (operandIndex < 0) {
            return null;
        }
        AbstractInsnNode operand =
                constructor.instructions.get(operandIndex);
        if (!isDirectDeclaredIntArgumentLoad(
                operand, declaredArguments)
                && (operand.getOpcode() != Opcodes.ILOAD
                || !prefixIntCopies.contains(
                ((VarInsnNode) operand).var))) {
            return null;
        }
        return previousExecutableIndex(constructor, operandIndex - 1);
    }

    /**
     * Proves the exact retained-prefix int computation
     * {@code ALOAD array; index; xALOAD}, where {@code xALOAD} is
     * {@code IALOAD}, {@code BALOAD}, {@code CALOAD}, or {@code SALOAD} and
     * the array type exactly matches that opcode. The index must be a constant
     * or one single-instruction declared/proven-copy ILOAD. The source must be
     * an unchanged declared argument or its proven prefix extra-local copy,
     * and no earlier array store is accepted. The load remains JVM bytecode,
     * preserving null, bounds, and primitive widening behavior without
     * reproducing it in native code.
     */
    private static Integer previousProvenIntArrayLoadLeaf(
            MethodNode constructor, int inputIndex,
            Map<Integer, Type> declaredArguments,
            Map<Integer, Integer> prefixArrayCopies,
            Set<Integer> prefixIntCopies) {
        AbstractInsnNode input = constructor.instructions.get(inputIndex);
        int loadOpcode = input.getOpcode();
        if (loadOpcode != Opcodes.IALOAD
                && loadOpcode != Opcodes.BALOAD
                && loadOpcode != Opcodes.CALOAD
                && loadOpcode != Opcodes.SALOAD) {
            return null;
        }
        int indexIndex =
                previousExecutableIndex(constructor, inputIndex - 1);
        if (indexIndex < 0) {
            return null;
        }
        int beforeSingleIndex =
                previousExecutableIndex(constructor, indexIndex - 1);
        AbstractInsnNode index = constructor.instructions.get(indexIndex);
        if (!isIntFamilyConstant(index)) {
            if (index.getOpcode() != Opcodes.ILOAD) {
                return null;
            }
            Integer beforeIndex = previousProvenIntChainLeaf(
                    constructor, indexIndex, declaredArguments,
                    prefixArrayCopies, prefixIntCopies);
            if (beforeIndex == null
                    || beforeIndex != beforeSingleIndex) {
                return null;
            }
        }
        int arrayIndex = beforeSingleIndex;
        if (arrayIndex < 0) {
            return null;
        }
        AbstractInsnNode array = constructor.instructions.get(arrayIndex);
        if (!(array instanceof VarInsnNode)
                || array.getOpcode() != Opcodes.ALOAD) {
            return null;
        }
        int loadedArrayLocal = ((VarInsnNode) array).var;
        int declaredArrayLocal = loadedArrayLocal;
        Type declaredArray = declaredArguments.get(loadedArrayLocal);
        if (declaredArray == null) {
            Integer copiedFrom = prefixArrayCopies.get(loadedArrayLocal);
            if (copiedFrom == null) {
                return null;
            }
            declaredArrayLocal = copiedFrom;
            declaredArray = declaredArguments.get(declaredArrayLocal);
        }
        if (declaredArray == null
                || !matchesIntArrayLoadType(
                loadOpcode, declaredArray)) {
            return null;
        }
        for (int i = 0; i < inputIndex; i++) {
            AbstractInsnNode instruction =
                    constructor.instructions.get(i);
            Type stored = storeType(instruction);
            if (i < arrayIndex && stored != null
                    && localRangesOverlap(
                    ((VarInsnNode) instruction).var, stored,
                    declaredArrayLocal, declaredArray)) {
                return null;
            }
            if (isArrayStoreOpcode(instruction.getOpcode())) {
                return null;
            }
        }
        return previousExecutableIndex(constructor, arrayIndex - 1);
    }

    private static boolean matchesIntArrayLoadType(
            int loadOpcode, Type declaredArray) {
        if (loadOpcode == Opcodes.IALOAD) {
            return Type.getType("[I").equals(declaredArray);
        }
        if (loadOpcode == Opcodes.BALOAD) {
            return Type.getType("[B").equals(declaredArray)
                    || Type.getType("[Z").equals(declaredArray);
        }
        if (loadOpcode == Opcodes.CALOAD) {
            return Type.getType("[C").equals(declaredArray);
        }
        return loadOpcode == Opcodes.SALOAD
                && Type.getType("[S").equals(declaredArray);
    }

    private static boolean isProvenIntChainBinary(int opcode) {
        return opcode == Opcodes.IADD
                || opcode == Opcodes.ISUB
                || opcode == Opcodes.IMUL
                || opcode == Opcodes.IAND
                || opcode == Opcodes.IOR
                || opcode == Opcodes.IXOR
                || opcode == Opcodes.ISHL
                || opcode == Opcodes.ISHR
                || opcode == Opcodes.IUSHR
                || opcode == Opcodes.IDIV
                || opcode == Opcodes.IREM;
    }

    private static boolean isDirectDeclaredArgumentLoad(
            AbstractInsnNode input, Type expected,
            Map<Integer, Type> declaredArguments) {
        if (!(input instanceof VarInsnNode)) {
            return false;
        }
        VarInsnNode load = (VarInsnNode) input;
        Type declared = declaredArguments.get(load.var);
        return declared != null
                && load.getOpcode() == declared.getOpcode(Opcodes.ILOAD)
                && sameInvocationCarrier(declared, expected);
    }

    private static boolean isDirectDeclaredIntArgumentLoad(
            AbstractInsnNode input,
            Map<Integer, Type> declaredArguments) {
        if (!(input instanceof VarInsnNode)
                || input.getOpcode() != Opcodes.ILOAD) {
            return false;
        }
        Type declared =
                declaredArguments.get(((VarInsnNode) input).var);
        return declared != null && isIntFamily(declared);
    }

    private static boolean isIntFamilyConstant(AbstractInsnNode input) {
        int opcode = input.getOpcode();
        return opcode >= Opcodes.ICONST_M1
                && opcode <= Opcodes.ICONST_5
                || opcode == Opcodes.BIPUSH
                || opcode == Opcodes.SIPUSH
                || opcode == Opcodes.LDC
                && ((LdcInsnNode) input).cst instanceof Integer;
    }

    private static boolean sameInvocationCarrier(
            Type declared, Type expected) {
        if (declared.equals(expected)) {
            return true;
        }
        return isIntFamily(declared) && isIntFamily(expected);
    }

    private static boolean isIntFamily(Type type) {
        int sort = type.getSort();
        return sort == Type.BOOLEAN || sort == Type.BYTE
                || sort == Type.CHAR || sort == Type.SHORT
                || sort == Type.INT;
    }

    private static void normalizeDuplicatedSuffix(
            MethodNode constructor, DuplicatedSuffix suffix) {
        List<AbstractInsnNode> calls = new ArrayList<>();
        List<List<AbstractInsnNode>> discardedCopies = new ArrayList<>();
        for (DuplicatedRange range : suffix.discarded) {
            calls.add(constructor.instructions.get(range.callIndex));
            List<AbstractInsnNode> copy = new ArrayList<>();
            for (int i = range.startIndex; i < range.endIndex; i++) {
                copy.add(constructor.instructions.get(i));
            }
            discardedCopies.add(copy);
        }
        AbstractInsnNode canonicalCall =
                constructor.instructions.get(suffix.canonicalCallIndex);
        for (List<AbstractInsnNode> copy : discardedCopies) {
            for (AbstractInsnNode instruction : copy) {
                constructor.instructions.remove(instruction);
            }
        }

        LabelNode join = new LabelNode();
        for (AbstractInsnNode call : calls) {
            constructor.instructions.insert(
                    call, new JumpInsnNode(Opcodes.GOTO, join));
        }
        constructor.instructions.insert(canonicalCall, join);
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

    private static int previousExecutableIndex(
            MethodNode method, int startIndex) {
        for (int i = startIndex; i >= 0; i--) {
            if (method.instructions.get(i).getOpcode() >= 0) {
                return i;
            }
        }
        return -1;
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

    private static boolean hasReceiverStoreBefore(
            MethodNode constructor, int endIndex) {
        for (int i = 0; i < endIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 0) {
                return true;
            }
        }
        return false;
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

    private static List<ExtraLocal> distinctExtraLocals(
            MethodNode constructor, DistinctSuffix distinctSuffix,
            int diagnosticCallIndex) {
        int firstExtraLocal = firstExtraLocal(constructor);
        Map<Integer, Type> suffixReads = new TreeMap<>();
        Set<Integer> suffixAccesses = new HashSet<>();
        for (LinearSuffix suffix : distinctSuffix.suffixes) {
            for (int i = suffix.startIndex; i < suffix.endIndex; i++) {
                AbstractInsnNode instruction =
                        constructor.instructions.get(i);
                int read = readLocal(instruction);
                Type readType = loadType(instruction);
                if (read >= firstExtraLocal && readType != null) {
                    Type previous = suffixReads.put(read, readType);
                    if (previous != null && !previous.equals(readType)) {
                        throw unsupported(
                                "Constructor distinct suffixes read extra local "
                                        + read + " with incompatible types",
                                i, instruction);
                    }
                    suffixAccesses.add(read);
                }
                Type stored = storeType(instruction);
                if (stored != null) {
                    int local = ((VarInsnNode) instruction).var;
                    if (local >= firstExtraLocal) {
                        suffixAccesses.add(local);
                    }
                }
            }
        }

        List<ExtraLocal> extras = new ArrayList<>();
        int packedLocal = firstExtraLocal;
        for (Map.Entry<Integer, Type> suffixRead : suffixReads.entrySet()) {
            int local = suffixRead.getKey();
            int[] states = localStates(constructor, local);
            Type provenType = null;
            for (Integer callIndex : distinctSuffix.callIndexes) {
                int state = states[callIndex];
                if ((state & LOCAL_UNASSIGNED) != 0 || state == 0) {
                    throw unsupported(
                            "Constructor prefix extra local " + local
                                    + " is not definitely assigned on every "
                                    + "path reaching a distinct-suffix bridge",
                            callIndex,
                            constructor.instructions.get(callIndex));
                }
                Type callType = singleStateType(state);
                if (callType == null
                        || provenType != null && !provenType.equals(callType)) {
                    throw unsupported(
                            "Constructor prefix extra local " + local
                                    + " does not have one provable type at "
                                    + "every distinct-suffix bridge",
                            callIndex,
                            constructor.instructions.get(callIndex));
                }
                provenType = callType;
            }
            if (!suffixRead.getValue().equals(provenType)) {
                throw unsupported(
                        "Constructor prefix extra local " + local
                                + " is stored as "
                                + provenType.getDescriptor()
                                + " but read by a distinct suffix as "
                                + suffixRead.getValue().getDescriptor(),
                        diagnosticCallIndex,
                        constructor.instructions.get(diagnosticCallIndex));
            }
            for (Map.Entry<Integer, Type> otherRead :
                    suffixReads.entrySet()) {
                if (otherRead.getKey() != local
                        && localRangesOverlap(
                        local, provenType,
                        otherRead.getKey(), otherRead.getValue())) {
                    throw unsupported(
                            "Constructor distinct suffixes read overlapping "
                                    + "category-2 extra local slots at " + local,
                            diagnosticCallIndex,
                            constructor.instructions.get(diagnosticCallIndex));
                }
            }
            extras.add(new ExtraLocal(local, packedLocal, provenType));
            packedLocal += provenType.getSize();
        }

        Set<Integer> forwarded = new HashSet<>();
        for (ExtraLocal extra : extras) {
            forwarded.add(extra.index);
        }
        suffixAccesses.removeAll(forwarded);
        if (!suffixAccesses.isEmpty()) {
            int local = suffixAccesses.iterator().next();
            throw unsupported(
                    "Constructor distinct suffix uses extra local " + local
                            + " without a proven prefix value",
                    diagnosticCallIndex,
                    constructor.instructions.get(diagnosticCallIndex));
        }
        return extras;
    }

    private static int[] localStates(
            MethodNode constructor, int local) {
        int[] states = new int[constructor.instructions.size()];
        states[0] = LOCAL_UNASSIGNED;
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        Map<LabelNode, Integer> labelIndexes = labelIndexes(constructor);
        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            int output = transferLocalState(
                    states[index], constructor.instructions.get(index), local);
            for (Integer successor :
                    normalSuccessors(constructor, index, labelIndexes)) {
                int merged = states[successor] | output;
                if (merged != states[successor]) {
                    states[successor] = merged;
                    pending.add(successor);
                }
            }
        }
        return states;
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
        return localStatesToSplit(constructor, splitIndex, local)[splitIndex];
    }

    private static int[] localStatesToSplit(
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
        return states;
    }

    private static boolean hasConditionalExtraForPrefixExit(
            MethodNode constructor, int splitIndex,
            Set<Integer> prefixExitCallIndexes,
            List<ExtraLocal> extraLocals) {
        for (ExtraLocal extra : extraLocals) {
            int[] states =
                    localStatesToSplit(constructor, splitIndex, extra.index);
            for (Integer callIndex : prefixExitCallIndexes) {
                if ((states[callIndex] & LOCAL_UNASSIGNED) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Proves that every retained ASTORE 0 is reachable with a stack input and
     * that each selected chain call consumes the original constructor receiver.
     * A single-call constructor, a strict shared-join diamond (including an
     * identical-copy constructor after normalization), or a bounded
     * path-selected distinct-suffix constructor may forward that receiver
     * through an alias while local 0 receives another reference. Other
     * multi-call forms remain limited to identity-preserving stores.
     */
    private static boolean validateReceiverStores(
            MethodNode constructor, int splitIndex, List<Integer> callIndexes,
            List<TryCatchBlockNode> prefixTryCatches,
            boolean allowAliasForwarding) {
        List<Integer> receiverStores = new ArrayList<>();
        for (int i = 0; i < splitIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 0) {
                receiverStores.add(i);
            }
        }
        if (receiverStores.isEmpty()) {
            return false;
        }

        int diagnosticIndex = receiverStores.get(0);
        ReceiverFrame[] frames;
        try {
            frames = receiverFrames(
                    constructor, splitIndex, prefixTryCatches);
        } catch (ReceiverAnalysisFailure failure) {
            throw unsupported(
                    "Constructor prefix ASTORE 0 does not provably preserve "
                            + "the constructor receiver"
                            + " (receiver analysis failed at instruction "
                            + failure.instructionIndex + ")",
                    diagnosticIndex,
                    constructor.instructions.get(diagnosticIndex));
        }

        boolean receiverAliasForwarding = false;
        for (Integer storeIndex : receiverStores) {
            ReceiverFrame frame = frames[storeIndex];
            if (frame == null || frame.stack.isEmpty()
                    || (!allowAliasForwarding
                    && !frame.stack.get(frame.stack.size() - 1).receiver)) {
                throw unsupported(
                        "Constructor prefix ASTORE 0 does not provably preserve "
                                + "the constructor receiver",
                        storeIndex, constructor.instructions.get(storeIndex));
            }
            receiverAliasForwarding |=
                    !frame.stack.get(frame.stack.size() - 1).receiver;
        }
        for (Integer callIndex : callIndexes) {
            ReceiverFrame frame = frames[callIndex];
            MethodInsnNode call =
                    (MethodInsnNode) constructor.instructions.get(callIndex);
            int receiverIndex = frame == null
                    ? -1
                    : frame.stack.size()
                    - Type.getArgumentTypes(call.desc).length - 1;
            if (receiverIndex < 0
                    || !frame.stack.get(receiverIndex).receiver) {
                throw unsupported(
                        "Constructor with prefix ASTORE 0 does not provably "
                                + "invoke this/super on the constructor receiver",
                        callIndex, call);
            }
        }
        return receiverAliasForwarding;
    }

    private static ReceiverFrame[] receiverFrames(
            MethodNode constructor, int splitIndex,
            List<TryCatchBlockNode> prefixTryCatches)
            throws ReceiverAnalysisFailure {
        int localCount = Math.max(constructor.maxLocals, 1);
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof VarInsnNode) {
                int size = instruction.getOpcode() == Opcodes.LLOAD
                        || instruction.getOpcode() == Opcodes.DLOAD
                        || instruction.getOpcode() == Opcodes.LSTORE
                        || instruction.getOpcode() == Opcodes.DSTORE ? 2 : 1;
                localCount = Math.max(
                        localCount, ((VarInsnNode) instruction).var + size);
            } else if (instruction instanceof IincInsnNode) {
                localCount = Math.max(
                        localCount, ((IincInsnNode) instruction).var + 1);
            }
        }

        ReceiverFrame[] frames = new ReceiverFrame[splitIndex + 1];
        ReceiverFrame entry = new ReceiverFrame(localCount);
        entry.locals[0] = true;
        frames[0] = entry;
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        boolean[] queued = new boolean[splitIndex + 1];
        queued[0] = true;
        Map<LabelNode, Integer> indexes = labelIndexes(constructor);

        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            queued[index] = false;
            if (index == splitIndex) {
                continue;
            }
            ReceiverFrame input = frames[index];
            AbstractInsnNode instruction =
                    constructor.instructions.get(index);
            ReceiverFrame output = new ReceiverFrame(input);
            transferReceiverFrame(output, instruction, index);

            for (Integer successor :
                    normalSuccessors(constructor, index, indexes)) {
                if (successor <= splitIndex
                        && mergeReceiverFrame(
                        frames, successor, output, index)
                        && !queued[successor]) {
                    pending.add(successor);
                    queued[successor] = true;
                }
            }

            for (TryCatchBlockNode tryCatch : prefixTryCatches) {
                int start = indexes.get(tryCatch.start);
                int end = indexes.get(tryCatch.end);
                int handler = indexes.get(tryCatch.handler);
                if (index < start || index >= end || handler > splitIndex) {
                    continue;
                }
                ReceiverFrame exceptionFrame = new ReceiverFrame(input);
                exceptionFrame.stack.clear();
                exceptionFrame.stack.add(ReceiverValue.OTHER_ONE);
                if (mergeReceiverFrame(
                        frames, handler, exceptionFrame, index)
                        && !queued[handler]) {
                    pending.add(handler);
                    queued[handler] = true;
                }
            }
        }
        return frames;
    }

    private static boolean mergeReceiverFrame(
            ReceiverFrame[] frames, int target, ReceiverFrame incoming,
            int instructionIndex) throws ReceiverAnalysisFailure {
        ReceiverFrame current = frames[target];
        if (current == null) {
            frames[target] = new ReceiverFrame(incoming);
            return true;
        }
        if (current.stack.size() != incoming.stack.size()) {
            throw new ReceiverAnalysisFailure(instructionIndex);
        }

        boolean changed = false;
        for (int i = 0; i < current.locals.length; i++) {
            boolean merged = current.locals[i] && incoming.locals[i];
            changed |= merged != current.locals[i];
            current.locals[i] = merged;
        }
        for (int i = 0; i < current.stack.size(); i++) {
            ReceiverValue left = current.stack.get(i);
            ReceiverValue right = incoming.stack.get(i);
            if (left.size != right.size) {
                throw new ReceiverAnalysisFailure(instructionIndex);
            }
            if (left.receiver && !right.receiver) {
                current.stack.set(i, left.size == 2
                        ? ReceiverValue.OTHER_TWO : ReceiverValue.OTHER_ONE);
                changed = true;
            }
        }
        return changed;
    }

    private static void transferReceiverFrame(
            ReceiverFrame frame, AbstractInsnNode instruction,
            int instructionIndex) throws ReceiverAnalysisFailure {
        int opcode = instruction.getOpcode();
        if (opcode < 0 || opcode == Opcodes.NOP) {
            return;
        }
        switch (opcode) {
            case Opcodes.ACONST_NULL:
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.LDC:
                Object constant = ((LdcInsnNode) instruction).cst;
                int constantSize = constant instanceof Long
                        || constant instanceof Double ? 2 : 1;
                if (constant instanceof ConstantDynamic) {
                    constantSize = Type.getType(
                            ((ConstantDynamic) constant).getDescriptor()).getSize();
                }
                frame.stack.add(constantSize == 2
                        ? ReceiverValue.OTHER_TWO : ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.ALOAD:
                int loadedLocal = ((VarInsnNode) instruction).var;
                frame.stack.add(frame.locals[loadedLocal]
                        ? ReceiverValue.RECEIVER : ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.ILOAD:
            case Opcodes.FLOAD:
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.LLOAD:
            case Opcodes.DLOAD:
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.ASTORE:
                ReceiverValue reference =
                        popReceiverValue(frame, 1, instructionIndex);
                frame.locals[((VarInsnNode) instruction).var] =
                        reference.receiver;
                return;
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
                popReceiverValue(frame, 1, instructionIndex);
                frame.locals[((VarInsnNode) instruction).var] = false;
                return;
            case Opcodes.LSTORE:
            case Opcodes.DSTORE:
                popReceiverValue(frame, 2, instructionIndex);
                int wideLocal = ((VarInsnNode) instruction).var;
                frame.locals[wideLocal] = false;
                frame.locals[wideLocal + 1] = false;
                return;
            case Opcodes.IINC:
                frame.locals[((IincInsnNode) instruction).var] = false;
                return;
            case Opcodes.IALOAD:
            case Opcodes.FALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.LALOAD:
            case Opcodes.DALOAD:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.IASTORE:
            case Opcodes.FASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.LASTORE:
            case Opcodes.DASTORE:
                popReceiverValue(frame, 2, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.POP:
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.POP2:
                ReceiverValue popped =
                        popReceiverValue(frame, 0, instructionIndex);
                if (popped.size == 1) {
                    popReceiverValue(frame, 1, instructionIndex);
                }
                return;
            case Opcodes.DUP:
            case Opcodes.DUP_X1:
            case Opcodes.DUP_X2:
            case Opcodes.DUP2:
            case Opcodes.DUP2_X1:
            case Opcodes.DUP2_X2:
            case Opcodes.SWAP:
                transferReceiverStackShuffle(frame, opcode, instructionIndex);
                return;
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM:
                popReceiverValue(frame, 2, instructionIndex);
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.INEG:
            case Opcodes.FNEG:
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.LNEG:
            case Opcodes.DNEG:
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.I2L:
            case Opcodes.I2D:
            case Opcodes.F2L:
            case Opcodes.F2D:
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.I2F:
            case Opcodes.F2I:
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.L2I:
            case Opcodes.L2F:
            case Opcodes.D2I:
            case Opcodes.D2F:
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.L2D:
            case Opcodes.D2L:
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_TWO);
                return;
            case Opcodes.LCMP:
                popReceiverValue(frame, 2, instructionIndex);
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                popReceiverValue(frame, 2, instructionIndex);
                popReceiverValue(frame, 2, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                popReceiverValue(frame, 1, instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.GOTO:
                return;
            case Opcodes.JSR:
            case Opcodes.RET:
                throw new ReceiverAnalysisFailure(instructionIndex);
            case Opcodes.IRETURN:
            case Opcodes.FRETURN:
            case Opcodes.ARETURN:
            case Opcodes.ATHROW:
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.LRETURN:
            case Opcodes.DRETURN:
                popReceiverValue(frame, 2, instructionIndex);
                return;
            case Opcodes.RETURN:
                return;
            case Opcodes.GETSTATIC:
                pushReceiverType(
                        frame, Type.getType(((FieldInsnNode) instruction).desc));
                return;
            case Opcodes.PUTSTATIC:
                popReceiverValue(frame,
                        Type.getType(((FieldInsnNode) instruction).desc).getSize(),
                        instructionIndex);
                return;
            case Opcodes.GETFIELD:
                popReceiverValue(frame, 1, instructionIndex);
                pushReceiverType(
                        frame, Type.getType(((FieldInsnNode) instruction).desc));
                return;
            case Opcodes.PUTFIELD:
                popReceiverValue(frame,
                        Type.getType(((FieldInsnNode) instruction).desc).getSize(),
                        instructionIndex);
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                popReceiverArguments(
                        frame, invoke.desc,
                        opcode != Opcodes.INVOKESTATIC, instructionIndex);
                pushReceiverType(frame, Type.getReturnType(invoke.desc));
                return;
            case Opcodes.INVOKEDYNAMIC:
                InvokeDynamicInsnNode dynamic =
                        (InvokeDynamicInsnNode) instruction;
                popReceiverArguments(
                        frame, dynamic.desc, false, instructionIndex);
                pushReceiverType(frame, Type.getReturnType(dynamic.desc));
                return;
            case Opcodes.NEW:
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.NEWARRAY:
            case Opcodes.ANEWARRAY:
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.ARRAYLENGTH:
            case Opcodes.INSTANCEOF:
                popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            case Opcodes.CHECKCAST:
                ReceiverValue castValue =
                        popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(castValue);
                return;
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                popReceiverValue(frame, 1, instructionIndex);
                return;
            case Opcodes.MULTIANEWARRAY:
                MultiANewArrayInsnNode multi =
                        (MultiANewArrayInsnNode) instruction;
                for (int i = 0; i < multi.dims; i++) {
                    popReceiverValue(frame, 1, instructionIndex);
                }
                frame.stack.add(ReceiverValue.OTHER_ONE);
                return;
            default:
                throw new ReceiverAnalysisFailure(instructionIndex);
        }
    }

    private static void popReceiverArguments(
            ReceiverFrame frame, String descriptor, boolean hasReceiver,
            int instructionIndex) throws ReceiverAnalysisFailure {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        for (int i = arguments.length - 1; i >= 0; i--) {
            popReceiverValue(
                    frame, arguments[i].getSize(), instructionIndex);
        }
        if (hasReceiver) {
            popReceiverValue(frame, 1, instructionIndex);
        }
    }

    private static void pushReceiverType(ReceiverFrame frame, Type type) {
        if (type.getSort() != Type.VOID) {
            frame.stack.add(type.getSize() == 2
                    ? ReceiverValue.OTHER_TWO : ReceiverValue.OTHER_ONE);
        }
    }

    private static ReceiverValue popReceiverValue(
            ReceiverFrame frame, int expectedSize, int instructionIndex)
            throws ReceiverAnalysisFailure {
        if (frame.stack.isEmpty()) {
            throw new ReceiverAnalysisFailure(instructionIndex);
        }
        ReceiverValue value = frame.stack.remove(frame.stack.size() - 1);
        if (expectedSize != 0 && value.size != expectedSize) {
            throw new ReceiverAnalysisFailure(instructionIndex);
        }
        return value;
    }

    private static void transferReceiverStackShuffle(
            ReceiverFrame frame, int opcode, int instructionIndex)
            throws ReceiverAnalysisFailure {
        ReceiverValue value1 =
                popReceiverValue(frame, 0, instructionIndex);
        if (opcode == Opcodes.DUP) {
            requireReceiverSize(value1, 1, instructionIndex);
            frame.stack.add(value1);
            frame.stack.add(value1);
            return;
        }
        ReceiverValue value2 =
                popReceiverValue(frame, 0, instructionIndex);
        if (opcode == Opcodes.SWAP) {
            requireReceiverSize(value1, 1, instructionIndex);
            requireReceiverSize(value2, 1, instructionIndex);
            frame.stack.add(value1);
            frame.stack.add(value2);
            return;
        }
        if (opcode == Opcodes.DUP_X1) {
            requireReceiverSize(value1, 1, instructionIndex);
            requireReceiverSize(value2, 1, instructionIndex);
            frame.stack.add(value1);
            frame.stack.add(value2);
            frame.stack.add(value1);
            return;
        }
        if (opcode == Opcodes.DUP_X2) {
            requireReceiverSize(value1, 1, instructionIndex);
            if (value2.size == 2) {
                frame.stack.add(value1);
                frame.stack.add(value2);
                frame.stack.add(value1);
                return;
            }
            ReceiverValue value3 =
                    popReceiverValue(frame, 1, instructionIndex);
            frame.stack.add(value1);
            frame.stack.add(value3);
            frame.stack.add(value2);
            frame.stack.add(value1);
            return;
        }
        if (opcode == Opcodes.DUP2) {
            if (value1.size == 2) {
                frame.stack.add(value1);
                frame.stack.add(value1);
                return;
            }
            requireReceiverSize(value2, 1, instructionIndex);
            frame.stack.add(value2);
            frame.stack.add(value1);
            frame.stack.add(value2);
            frame.stack.add(value1);
            return;
        }
        if (opcode == Opcodes.DUP2_X1) {
            if (value1.size == 2) {
                requireReceiverSize(value2, 1, instructionIndex);
                frame.stack.add(value1);
                frame.stack.add(value2);
                frame.stack.add(value1);
                return;
            }
            requireReceiverSize(value2, 1, instructionIndex);
            ReceiverValue value3 =
                    popReceiverValue(frame, 1, instructionIndex);
            frame.stack.add(value2);
            frame.stack.add(value1);
            frame.stack.add(value3);
            frame.stack.add(value2);
            frame.stack.add(value1);
            return;
        }
        if (opcode == Opcodes.DUP2_X2) {
            if (value1.size == 2) {
                if (value2.size == 2) {
                    frame.stack.add(value1);
                    frame.stack.add(value2);
                    frame.stack.add(value1);
                    return;
                }
                ReceiverValue value3 =
                        popReceiverValue(frame, 1, instructionIndex);
                frame.stack.add(value1);
                frame.stack.add(value3);
                frame.stack.add(value2);
                frame.stack.add(value1);
                return;
            }
            requireReceiverSize(value2, 1, instructionIndex);
            ReceiverValue value3 =
                    popReceiverValue(frame, 0, instructionIndex);
            if (value3.size == 2) {
                frame.stack.add(value2);
                frame.stack.add(value1);
                frame.stack.add(value3);
                frame.stack.add(value2);
                frame.stack.add(value1);
                return;
            }
            ReceiverValue value4 =
                    popReceiverValue(frame, 1, instructionIndex);
            frame.stack.add(value2);
            frame.stack.add(value1);
            frame.stack.add(value4);
            frame.stack.add(value3);
            frame.stack.add(value2);
            frame.stack.add(value1);
            return;
        }
        throw new ReceiverAnalysisFailure(instructionIndex);
    }

    private static void requireReceiverSize(
            ReceiverValue value, int size, int instructionIndex)
            throws ReceiverAnalysisFailure {
        if (value.size != size) {
            throw new ReceiverAnalysisFailure(instructionIndex);
        }
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
                new Type[arguments.length + split.extraLocals.size()
                        + (split.receiverAliasForwarding ? 1 : 0)
                        + (split.distinctSuffix == null ? 0 : 1)];
        System.arraycopy(arguments, 0, splitArguments, 0, arguments.length);
        for (int i = 0; i < split.extraLocals.size(); i++) {
            splitArguments[arguments.length + i] =
                    split.extraLocals.get(i).type;
        }
        if (split.receiverAliasForwarding) {
            splitArguments[arguments.length + split.extraLocals.size()] =
                    Type.getType(Class.class);
        }
        if (split.distinctSuffix != null) {
            splitArguments[splitArguments.length - 1] = Type.INT_TYPE;
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
        // packed parameters and, for path-selected bodies, the selector;
        // only this independent clone is rewritten.
        int remapped = split.packedExtraEnd
                + (split.receiverAliasForwarding ? 1 : 0)
                + (split.distinctSuffix == null ? 0 : 1)
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

    private static InsnList cloneRange(
            MethodNode method, int start, int end,
            Map<LabelNode, LabelNode> labels,
            List<RelocatedPrefixHandler> excludedHandlers) {
        InsnList copy = new InsnList();
        for (int i = start; i < end; i++) {
            boolean excluded = false;
            for (RelocatedPrefixHandler handler : excludedHandlers) {
                if (handler.contains(i)) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) {
                continue;
            }
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
        private final List<TryCatchBlockNode> prefixTryCatches;
        private final List<TryCatchBlockNode> suffixTryCatches;
        private final List<RelocatedPrefixHandler> relocatedPrefixHandlers;
        private final boolean receiverAliasForwarding;
        private final DuplicatedSuffix duplicatedSuffix;
        private final DistinctSuffix distinctSuffix;

        private ConstructorSplit(
                int suffixStartIndex, int wrapperEndIndex,
                Set<Integer> widenedReferenceLocals,
                List<ExtraLocal> extraLocals, int firstExtraLocal,
                List<TryCatchBlockNode> prefixTryCatches,
                List<TryCatchBlockNode> suffixTryCatches,
                List<RelocatedPrefixHandler> relocatedPrefixHandlers,
                boolean receiverAliasForwarding,
                DuplicatedSuffix duplicatedSuffix,
                DistinctSuffix distinctSuffix) {
            this.suffixStartIndex = suffixStartIndex;
            this.wrapperEndIndex = wrapperEndIndex;
            this.widenedReferenceLocals = widenedReferenceLocals;
            this.extraLocals = extraLocals;
            this.firstExtraLocal = firstExtraLocal;
            this.prefixTryCatches = prefixTryCatches;
            this.suffixTryCatches = suffixTryCatches;
            this.relocatedPrefixHandlers = relocatedPrefixHandlers;
            this.receiverAliasForwarding = receiverAliasForwarding;
            this.duplicatedSuffix = duplicatedSuffix;
            this.distinctSuffix = distinctSuffix;
            int packedLocal = firstExtraLocal;
            for (ExtraLocal extra : extraLocals) {
                packedLocal += extra.type.getSize();
            }
            this.packedExtraEnd = packedLocal;
        }
    }

    private static final class RelocatedPrefixHandler {
        private final int startIndex;
        private final int endIndex;
        private final int returnStartIndex;
        private final int returnEndIndex;

        private RelocatedPrefixHandler(int startIndex, int endIndex) {
            this(startIndex, endIndex, -1, -1);
        }

        private RelocatedPrefixHandler(
                int startIndex, int endIndex,
                int returnStartIndex, int returnEndIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.returnStartIndex = returnStartIndex;
            this.returnEndIndex = returnEndIndex;
        }

        private boolean contains(int index) {
            return (index >= startIndex && index < endIndex)
                    || (index >= returnStartIndex
                    && index < returnEndIndex);
        }
    }

    private static final class SharedSuffix {
        private final int joinIndex;
        private final Set<Integer> branchIndexes;
        private final Set<Integer> prefixExitCallIndexes;
        private final boolean strictDiamond;

        private SharedSuffix(
                int joinIndex, Set<Integer> branchIndexes,
                Set<Integer> prefixExitCallIndexes,
                boolean strictDiamond) {
            this.joinIndex = joinIndex;
            this.branchIndexes = branchIndexes;
            this.prefixExitCallIndexes = prefixExitCallIndexes;
            this.strictDiamond = strictDiamond;
        }
    }

    private static final class LinearSuffix {
        private final int startIndex;
        private final int endIndex;

        private LinearSuffix(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private static final class MultiSuperTryCatches {
        private final List<TryCatchBlockNode> prefix;
        private final List<TryCatchBlockNode> suffix;
        private final List<RelocatedPrefixHandler> relocated;

        private MultiSuperTryCatches(
                List<TryCatchBlockNode> prefix,
                List<TryCatchBlockNode> suffix,
                List<RelocatedPrefixHandler> relocated) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.relocated = relocated;
        }
    }

    private static final class DuplicatedSuffix {
        private final List<DuplicatedRange> discarded;
        private final int canonicalCallIndex;
        private final int canonicalStartIndex;

        private DuplicatedSuffix(
                List<DuplicatedRange> discarded, int canonicalCallIndex,
                int canonicalStartIndex) {
            this.discarded = discarded;
            this.canonicalCallIndex = canonicalCallIndex;
            this.canonicalStartIndex = canonicalStartIndex;
        }
    }

    private static final class DistinctSuffix {
        private final List<Integer> callIndexes;
        private final List<LinearSuffix> suffixes;

        private DistinctSuffix(
                List<Integer> callIndexes, List<LinearSuffix> suffixes) {
            this.callIndexes = callIndexes;
            this.suffixes = suffixes;
        }
    }

    private static final class DuplicatedRange {
        private final int callIndex;
        private final int startIndex;
        private final int endIndex;

        private DuplicatedRange(
                int callIndex, int startIndex, int endIndex) {
            this.callIndex = callIndex;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
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

    private static final class ReceiverFrame {
        private final boolean[] locals;
        private final List<ReceiverValue> stack;

        private ReceiverFrame(int localCount) {
            this.locals = new boolean[localCount];
            this.stack = new ArrayList<>();
        }

        private ReceiverFrame(ReceiverFrame source) {
            this.locals = source.locals.clone();
            this.stack = new ArrayList<>(source.stack);
        }
    }

    private static final class ReceiverValue {
        private static final ReceiverValue RECEIVER =
                new ReceiverValue(true, 1);
        private static final ReceiverValue OTHER_ONE =
                new ReceiverValue(false, 1);
        private static final ReceiverValue OTHER_TWO =
                new ReceiverValue(false, 2);

        private final boolean receiver;
        private final int size;

        private ReceiverValue(boolean receiver, int size) {
            this.receiver = receiver;
            this.size = size;
        }
    }

    private static final class ReceiverAnalysisFailure extends Exception {
        private final int instructionIndex;

        private ReceiverAnalysisFailure(int instructionIndex) {
            this.instructionIndex = instructionIndex;
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
