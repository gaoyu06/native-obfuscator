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
    private List<TryCatchBlockNode> retainedPrefixTryCatches = new ArrayList<>();
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
        relocatedPrefixHandlers =
                new ArrayList<>(split.relocatedPrefixHandlers);
        // The JNI shell only needs catch metadata for the native suffix.
        // Prefix catches are restored with cloned wrapper labels in postProcess.
        context.method.tryCatchBlocks.clear();
        context.method.tryCatchBlocks.addAll(split.suffixTryCatches);
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
        Map<LabelNode, LabelNode> labels = labels(context.method);
        InsnList wrapper = cloneRange(
                context.method, 0, split.wrapperEndIndex, labels,
                relocatedPrefixHandlers);
        List<TryCatchBlockNode> wrapperTryCatches = new ArrayList<>();
        for (TryCatchBlockNode tryCatch : retainedPrefixTryCatches) {
            wrapperTryCatches.add(new TryCatchBlockNode(
                    labels.get(tryCatch.start), labels.get(tryCatch.end),
                    labels.get(tryCatch.handler), tryCatch.type));
        }
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
        context.method.tryCatchBlocks.addAll(wrapperTryCatches);
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
        for (RelocatedPrefixHandler handler :
                split.relocatedPrefixHandlers) {
            for (int i = handler.startIndex; i < handler.endIndex; i++) {
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
        for (TryCatchBlockNode tryCatch : split.suffixTryCatches) {
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
        Set<Integer> admittedPrefixSuffixBranches = new HashSet<>();
        DuplicatedSuffix duplicatedSuffix = null;
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
                    throw unsupported(
                            "Constructor chain calls do not share one suffix join",
                            diagnosticCallIndex,
                            constructor.instructions.get(diagnosticCallIndex));
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
                        relocatablePrefixReturnHandler(
                                constructor, prefixLabels, suffixLabels,
                                instructionIndexes, tryCatch);
                if (relocated == null) {
                    throw new UnsupportedIrConstructException(
                            "Constructor exception regions may not cross the this/super split");
                }
                suffixTryCatches.add(tryCatch);
                relocatedByLabel.put(tryCatch.handler, relocated);
            }
        }
        validateReceiverStores(
                constructor, suffixStartIndex, callIndexes, prefixTryCatches);
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
                duplicatedSuffix);
    }

    private static boolean containsTryCatchLabels(
            Set<LabelNode> labels, TryCatchBlockNode tryCatch) {
        return labels.contains(tryCatch.start)
                && labels.contains(tryCatch.end)
                && labels.contains(tryCatch.handler);
    }

    /**
     * Proves the one prefix-handler shape that can be moved without changing
     * its exception behavior. The protected range is wholly in the suffix, and
     * the isolated handler consumes the caught exception and returns. With no
     * normal incoming edge or other range role, the handler can be removed from
     * the wrapper and cloned into the suffix with the original table entry.
     */
    private static RelocatedPrefixHandler relocatablePrefixReturnHandler(
            MethodNode constructor,
            Set<LabelNode> prefixLabels, Set<LabelNode> suffixLabels,
            Map<LabelNode, Integer> instructionIndexes,
            TryCatchBlockNode tryCatch) {
        if (!suffixLabels.contains(tryCatch.start)
                || !suffixLabels.contains(tryCatch.end)
                || !prefixLabels.contains(tryCatch.handler)) {
            return null;
        }
        Integer handlerIndex = instructionIndexes.get(tryCatch.handler);
        if (handlerIndex == null) {
            return null;
        }
        int popIndex =
                firstExecutableIndex(constructor, handlerIndex + 1);
        int returnIndex =
                firstExecutableIndex(constructor, popIndex + 1);
        int previousIndex =
                previousExecutableIndex(constructor, handlerIndex - 1);
        if (popIndex >= constructor.instructions.size()
                || returnIndex >= constructor.instructions.size()
                || previousIndex < 0
                || constructor.instructions.get(popIndex)
                .getOpcode() != Opcodes.POP
                || constructor.instructions.get(returnIndex)
                .getOpcode() != Opcodes.RETURN
                || constructor.instructions.get(previousIndex)
                .getOpcode() != Opcodes.GOTO
                || !containsOnlyFrames(
                        constructor, handlerIndex + 1, popIndex)
                || !containsOnlyFrames(
                        constructor, popIndex + 1, returnIndex)
                || hasNormalTarget(constructor, tryCatch.handler)
                || isTryRangeBoundary(constructor, tryCatch.handler)
                || !isOnlyUsedBySuffixRanges(
                        constructor, suffixLabels, tryCatch.handler)) {
            return null;
        }
        return new RelocatedPrefixHandler(
                handlerIndex, returnIndex + 1);
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
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode
                    && ((JumpInsnNode) instruction).label == target) {
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
                        prefixExitCallIndexes);
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
                continue;
            }

            Set<Integer> switchBranches = switchJoinOrReturnBranches(
                    constructor, callIndexes, i, join, joinIndex);
            if (switchBranches == null) {
                return null;
            }
            branchIndexes.addAll(switchBranches);
        }
        return new SharedSuffix(
                joinIndex, branchIndexes, new HashSet<>());
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
     * Proves one additional two-call shape: both calls fall through to
     * straight-line, structurally identical suffix copies ending in RETURN.
     * The copy may be empty, making RETURN the immediate successor of each
     * call.
     *
     * <p>For three or more calls, only the empty-copy form is admitted. Every
     * call must receive the original local-0 receiver and direct loads of
     * declared constructor arguments, and must be immediately followed by
     * RETURN. The canonical final return can then be shared by replacing every
     * earlier return with a GOTO; this does not create multiple native exits.
     */
    private static DuplicatedSuffix duplicatedSuffix(
            MethodNode constructor, List<Integer> callIndexes) {
        if (callIndexes.size() < 2
                || !constructor.tryCatchBlocks.isEmpty()) {
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
        if (canonical.endIndex != constructor.instructions.size()) {
            return null;
        }

        if (callIndexes.size() == 2) {
            if (!sameLinearSuffix(
                    constructor, suffixes.get(0), canonical)) {
                return null;
            }
        } else {
            for (LinearSuffix suffix : suffixes) {
                if (suffix.endIndex != suffix.startIndex + 1
                        || constructor.instructions.get(suffix.startIndex)
                        .getOpcode() != Opcodes.RETURN) {
                    return null;
                }
            }
            if (!hasDirectDeclaredChainInputs(
                    constructor, callIndexes)) {
                return null;
            }
        }

        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 0) {
                return null;
            }
        }

        int firstExtraLocal = firstExtraLocal(constructor);
        for (int i = canonical.startIndex; i < canonical.endIndex; i++) {
            int local = readLocal(constructor.instructions.get(i));
            if (local >= firstExtraLocal) {
                return null;
            }
        }
        if (!hasEmptyChainEntryStacks(constructor, callIndexes)) {
            return null;
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

    private static LinearSuffix linearSuffix(
            MethodNode constructor, int callIndex) {
        for (int i = callIndex + 1;
             i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            int opcode = instruction.getOpcode();
            if (opcode < 0) {
                return null;
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
        int leftLength = left.endIndex - left.startIndex;
        int rightLength = right.endIndex - right.startIndex;
        if (leftLength != rightLength) {
            return false;
        }
        for (int i = 0; i < leftLength; i++) {
            if (!sameLinearInstruction(
                    constructor.instructions.get(left.startIndex + i),
                    constructor.instructions.get(right.startIndex + i))) {
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
     * Restricts the 3+-return normalization to calls whose complete operand
     * sequence is visible locally: ALOAD 0 followed only by direct loads of
     * declared constructor arguments in invocation order.
     */
    private static boolean hasDirectDeclaredChainInputs(
            MethodNode constructor, List<Integer> callIndexes) {
        Map<Integer, Type> declaredArguments = new HashMap<>();
        int declaredLocal = 1;
        for (Type argument : Type.getArgumentTypes(constructor.desc)) {
            declaredArguments.put(declaredLocal, argument);
            declaredLocal += argument.getSize();
        }

        for (Integer callIndex : callIndexes) {
            MethodInsnNode call =
                    (MethodInsnNode) constructor.instructions.get(callIndex);
            Type[] callArguments = Type.getArgumentTypes(call.desc);
            int inputIndex =
                    previousExecutableIndex(constructor, callIndex - 1);
            for (int i = callArguments.length - 1; i >= 0; i--) {
                if (inputIndex < 0) {
                    return false;
                }
                AbstractInsnNode input =
                        constructor.instructions.get(inputIndex);
                if (!(input instanceof VarInsnNode)) {
                    return false;
                }
                VarInsnNode load = (VarInsnNode) input;
                Type declared = declaredArguments.get(load.var);
                if (declared == null
                        || load.getOpcode()
                        != declared.getOpcode(Opcodes.ILOAD)
                        || !sameInvocationCarrier(
                        declared, callArguments[i])) {
                    return false;
                }
                inputIndex =
                        previousExecutableIndex(constructor, inputIndex - 1);
            }
            if (inputIndex < 0
                    || constructor.instructions.get(inputIndex).getOpcode()
                    != Opcodes.ALOAD
                    || ((VarInsnNode) constructor.instructions.get(inputIndex)).var
                    != 0) {
                return false;
            }
        }
        return true;
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
     * Proves that every retained ASTORE 0 writes the original constructor
     * receiver and that each selected chain call consumes that same receiver.
     * The analysis is deliberately local to constructors containing ASTORE 0,
     * so unrelated admission behavior is unchanged.
     */
    private static void validateReceiverStores(
            MethodNode constructor, int splitIndex, List<Integer> callIndexes,
            List<TryCatchBlockNode> prefixTryCatches) {
        List<Integer> receiverStores = new ArrayList<>();
        for (int i = 0; i < splitIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.ASTORE
                    && ((VarInsnNode) instruction).var == 0) {
                receiverStores.add(i);
            }
        }
        if (receiverStores.isEmpty()) {
            return;
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

        for (Integer storeIndex : receiverStores) {
            ReceiverFrame frame = frames[storeIndex];
            if (frame == null || frame.stack.isEmpty()
                    || !frame.stack.get(frame.stack.size() - 1).receiver) {
                throw unsupported(
                        "Constructor prefix ASTORE 0 does not provably preserve "
                                + "the constructor receiver",
                        storeIndex, constructor.instructions.get(storeIndex));
            }
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

    private static InsnList cloneRange(
            MethodNode method, int start, int end,
            Map<LabelNode, LabelNode> labels,
            List<RelocatedPrefixHandler> excludedHandlers) {
        InsnList copy = new InsnList();
        for (int i = start; i < end; i++) {
            boolean excluded = false;
            for (RelocatedPrefixHandler handler : excludedHandlers) {
                if (i >= handler.startIndex && i < handler.endIndex) {
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
        private final DuplicatedSuffix duplicatedSuffix;

        private ConstructorSplit(
                int suffixStartIndex, int wrapperEndIndex,
                Set<Integer> widenedReferenceLocals,
                List<ExtraLocal> extraLocals, int firstExtraLocal,
                List<TryCatchBlockNode> prefixTryCatches,
                List<TryCatchBlockNode> suffixTryCatches,
                List<RelocatedPrefixHandler> relocatedPrefixHandlers,
                DuplicatedSuffix duplicatedSuffix) {
            this.suffixStartIndex = suffixStartIndex;
            this.wrapperEndIndex = wrapperEndIndex;
            this.widenedReferenceLocals = widenedReferenceLocals;
            this.extraLocals = extraLocals;
            this.firstExtraLocal = firstExtraLocal;
            this.prefixTryCatches = prefixTryCatches;
            this.suffixTryCatches = suffixTryCatches;
            this.relocatedPrefixHandlers = relocatedPrefixHandlers;
            this.duplicatedSuffix = duplicatedSuffix;
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

        private RelocatedPrefixHandler(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private static final class SharedSuffix {
        private final int joinIndex;
        private final Set<Integer> branchIndexes;
        private final Set<Integer> prefixExitCallIndexes;

        private SharedSuffix(
                int joinIndex, Set<Integer> branchIndexes,
                Set<Integer> prefixExitCallIndexes) {
            this.joinIndex = joinIndex;
            this.branchIndexes = branchIndexes;
            this.prefixExitCallIndexes = prefixExitCallIndexes;
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
