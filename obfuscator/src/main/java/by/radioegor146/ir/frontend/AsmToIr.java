package by.radioegor146.ir.frontend;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrExceptionEdge;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Lowers the phase-two bytecode subset to typed block-local SSA. JVM locals and
 * operand-stack entries become block phi values; load/store bytecodes therefore
 * disappear into the SSA environment.
 */
public final class AsmToIr {
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    public IrMethod build(String owner, MethodNode method) {
        MethodShape shape = validateMethodShape(method);
        CfgBuilder.Graph graph = cfgBuilder.build(method);
        validateInstructions(graph);
        validateLocalTypes(method, shape, graph);

        Set<CfgBuilder.Block> reachable = graph.reachableBlocks();
        CfgBuilder.Block entry = graph.getBlocks().get(0);
        if (entry.getPredecessors().stream().anyMatch(reachable::contains)
                || entry.getExceptionPredecessors().stream().anyMatch(reachable::contains)) {
            throw new UnsupportedIrConstructException(
                    "A backedge to the JVM method entry is outside the phase-two subset");
        }
        validateHandlerEntries(graph, reachable);

        Map<CfgBuilder.Block, List<IrType>> stackTypes =
                computeStackTypes(graph, reachable, method, shape);
        DefiniteLocals definiteLocals = computeDefiniteLocals(method, shape, graph, reachable);

        IrMethod irMethod = new IrMethod(owner, method.name, method.desc,
                shape.staticMethod, shape.returnType);
        IrValue[] entryLocals = createParameters(irMethod, method, shape);

        Map<CfgBuilder.Block, IrBlock> irBlocks = new LinkedHashMap<>();
        for (CfgBuilder.Block rawBlock : graph.getBlocks()) {
            if (reachable.contains(rawBlock)) {
                irBlocks.put(rawBlock, irMethod.addBlock());
            }
        }
        for (CfgBuilder.Block rawBlock : graph.getBlocks()) {
            if (!reachable.contains(rawBlock)) {
                continue;
            }
            IrBlock irBlock = irBlocks.get(rawBlock);
            for (CfgBuilder.ExceptionEdge edge : rawBlock.getExceptionEdges()) {
                if (reachable.contains(edge.getHandler())) {
                    irBlock.addExceptionEdge(new IrExceptionEdge(edge.getCatchType(),
                            irBlocks.get(edge.getHandler())));
                }
            }
        }

        Map<CfgBuilder.Block, BlockInputs> inputs = new HashMap<>();
        for (CfgBuilder.Block rawBlock : graph.getBlocks()) {
            if (!reachable.contains(rawBlock)) {
                continue;
            }
            IrBlock irBlock = irBlocks.get(rawBlock);
            if (rawBlock == entry) {
                inputs.put(rawBlock, new BlockInputs(entryLocals.clone(),
                        Collections.<IrValue>emptyList(), new IrPhi[method.maxLocals],
                        new IrPhi[0]));
                continue;
            }

            IrValue[] locals = new IrValue[method.maxLocals];
            IrPhi[] localPhis = new IrPhi[method.maxLocals];
            for (int local = 0; local < method.maxLocals; local++) {
                IrType type = shape.localTypes[local];
                if (type != null && definiteLocals.in.get(rawBlock)[local]
                        && (!isWide(type)
                        || definiteLocals.in.get(rawBlock)[local + 1])) {
                    IrPhi phi = irMethod.addPhi(irBlock, type, IrPhi.SlotKind.LOCAL, local);
                    localPhis[local] = phi;
                    locals[local] = phi.getResult();
                }
            }

            List<IrType> blockStackTypes = stackTypes.get(rawBlock);
            List<IrValue> stack = new ArrayList<>();
            IrPhi[] stackPhis;
            if (!rawBlock.getExceptionPredecessors().isEmpty()) {
                if (!blockStackTypes.equals(
                        Collections.singletonList(IrType.REFERENCE))) {
                    throw new UnsupportedIrConstructException(
                            "Exception handler entry must have one reference on its stack");
                }
                IrValue caught = irMethod.newInstructionValue(IrType.REFERENCE);
                int offset = rawBlock.getInstructions().isEmpty() ? -1
                        : rawBlock.getInstructions().get(0).getOriginalIndex();
                irBlock.addInstruction(new IrNodes.CaughtException(caught, offset));
                stack.add(caught);
                stackPhis = new IrPhi[0];
            } else {
                stackPhis = new IrPhi[blockStackTypes.size()];
                int stackSlot = 0;
                for (int slot = 0; slot < blockStackTypes.size(); slot++) {
                    IrType type = blockStackTypes.get(slot);
                    IrPhi phi = irMethod.addPhi(irBlock, type,
                            IrPhi.SlotKind.STACK, stackSlot);
                    stackPhis[slot] = phi;
                    stack.add(phi.getResult());
                    stackSlot += type.getJvmSlots();
                }
            }
            inputs.put(rawBlock, new BlockInputs(locals, stack, localPhis, stackPhis));
        }

        Map<CfgBuilder.Block, ValueState> outputs = new HashMap<>();
        for (CfgBuilder.Block rawBlock : graph.getBlocks()) {
            if (reachable.contains(rawBlock)) {
                outputs.put(rawBlock, lowerBlock(irMethod, method, shape, graph, rawBlock,
                        irBlocks, inputs.get(rawBlock)));
            }
        }

        connectPhis(graph, reachable, inputs, outputs, irBlocks);
        return irMethod;
    }

    private MethodShape validateMethodShape(MethodNode method) {
        Type returnType = Type.getReturnType(method.desc);
        IrType irReturn;
        if (returnType.getSort() == Type.VOID) {
            irReturn = IrType.VOID;
        } else if (isIntLike(returnType)) {
            irReturn = IrType.I32;
        } else if (returnType.getSort() == Type.LONG) {
            irReturn = IrType.I64;
        } else if (returnType.getSort() == Type.FLOAT) {
            irReturn = IrType.F32;
        } else if (returnType.getSort() == Type.DOUBLE) {
            irReturn = IrType.F64;
        } else if (isReference(returnType)) {
            irReturn = IrType.REFERENCE;
        } else {
            throw new UnsupportedIrConstructException(
                    "Only void, scalar, and reference method returns are supported");
        }

        boolean staticMethod = (method.access & Opcodes.ACC_STATIC) != 0;
        int requiredLocals = staticMethod ? 0 : 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (!isIntLike(argument) && !isReference(argument)
                    && argument.getSort() != Type.LONG
                    && argument.getSort() != Type.FLOAT
                    && argument.getSort() != Type.DOUBLE) {
                throw new UnsupportedIrConstructException(
                        "Only scalar and reference arguments are supported");
            }
            requiredLocals += argument.getSize();
        }
        if (method.maxLocals < requiredLocals) {
            throw new UnsupportedIrConstructException("maxLocals is smaller than the parameters");
        }

        IrType[] localTypes = new IrType[method.maxLocals];
        boolean[] wideContinuations = new boolean[method.maxLocals];
        int local = 0;
        if (!staticMethod) {
            localTypes[local++] = IrType.REFERENCE;
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            IrType type = irType(argument);
            localTypes[local] = type;
            if (isWide(type)) {
                wideContinuations[local + 1] = true;
            }
            local += argument.getSize();
        }
        return new MethodShape(staticMethod, irReturn, localTypes, wideContinuations,
                requiredLocals);
    }

    private void validateHandlerEntries(CfgBuilder.Graph graph,
                                        Set<CfgBuilder.Block> reachable) {
        for (CfgBuilder.Block block : graph.getBlocks()) {
            if (!reachable.contains(block) || block.getExceptionPredecessors().isEmpty()) {
                continue;
            }
            if (block.getPredecessors().stream().anyMatch(reachable::contains)) {
                throw new UnsupportedIrConstructException(
                        "A handler entry with a normal predecessor is outside the phase-four subset");
            }
        }
    }

    private void validateInstructions(CfgBuilder.Graph graph) {
        for (CfgBuilder.Block block : graph.getBlocks()) {
            for (CfgBuilder.Instruction instruction : block.getInstructions()) {
                AbstractInsnNode node = instruction.getNode();
                int opcode = node.getOpcode();
                boolean supported;
                switch (node.getType()) {
                    case AbstractInsnNode.INSN:
                        supported = opcode == Opcodes.NOP
                                || opcode == Opcodes.ACONST_NULL
                                || opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5
                                || opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1
                                || opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2
                                || opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1
                                || opcode == Opcodes.DUP
                                || opcode == Opcodes.POP
                                || opcode == Opcodes.SWAP
                                || isWideStackOperation(opcode)
                                || isIntBinaryOp(opcode)
                                || isLongBinaryOp(opcode)
                                || isFloatingBinaryOp(opcode)
                                || isIntDivRem(opcode)
                                || isIntUnaryOp(opcode)
                                || isFloatingUnaryOp(opcode)
                                || isFloatingCompareOp(opcode)
                                || isConversionOp(opcode)
                                || opcode == Opcodes.IALOAD
                                || opcode == Opcodes.AALOAD
                                || opcode == Opcodes.IASTORE
                                || opcode == Opcodes.AASTORE
                                || opcode == Opcodes.ARRAYLENGTH
                                || opcode == Opcodes.ATHROW
                                || opcode == Opcodes.IRETURN
                                || opcode == Opcodes.LRETURN
                                || opcode == Opcodes.FRETURN
                                || opcode == Opcodes.DRETURN
                                || opcode == Opcodes.ARETURN
                                || opcode == Opcodes.RETURN;
                        break;
                    case AbstractInsnNode.INT_INSN:
                        supported = opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH
                                || opcode == Opcodes.NEWARRAY
                                && ((IntInsnNode) node).operand == Opcodes.T_INT;
                        break;
                    case AbstractInsnNode.VAR_INSN:
                        supported = opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE
                                || opcode == Opcodes.LLOAD || opcode == Opcodes.LSTORE
                                || opcode == Opcodes.FLOAD || opcode == Opcodes.FSTORE
                                || opcode == Opcodes.DLOAD || opcode == Opcodes.DSTORE
                                || opcode == Opcodes.ALOAD || opcode == Opcodes.ASTORE;
                        break;
                    case AbstractInsnNode.IINC_INSN:
                        supported = true;
                        break;
                    case AbstractInsnNode.LDC_INSN:
                        Object constant = ((LdcInsnNode) node).cst;
                        supported = constant instanceof Integer || constant instanceof Long
                                || constant instanceof Float || constant instanceof Double
                                || constant instanceof String
                                || isSupportedClassConstant(constant);
                        break;
                    case AbstractInsnNode.JUMP_INSN:
                        supported = opcode == Opcodes.GOTO || isUnaryIntJump(opcode)
                                || isBinaryIntJump(opcode) || isReferenceNullJump(opcode);
                        break;
                    case AbstractInsnNode.TABLESWITCH_INSN:
                    case AbstractInsnNode.LOOKUPSWITCH_INSN:
                        supported = true;
                        break;
                    case AbstractInsnNode.TYPE_INSN:
                        TypeInsnNode typeInsn = (TypeInsnNode) node;
                        supported = (opcode == Opcodes.NEW
                                || opcode == Opcodes.ANEWARRAY
                                || opcode == Opcodes.CHECKCAST
                                || opcode == Opcodes.INSTANCEOF)
                                && typeInsn.desc != null
                                && !typeInsn.desc.isEmpty()
                                && (opcode != Opcodes.NEW
                                || !typeInsn.desc.startsWith("["));
                        break;
                    case AbstractInsnNode.FIELD_INSN:
                        FieldInsnNode field = (FieldInsnNode) node;
                        supported = (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD
                                || opcode == Opcodes.GETSTATIC
                                || opcode == Opcodes.PUTSTATIC)
                                && isSupportedFieldDescriptor(field.desc);
                        break;
                    case AbstractInsnNode.METHOD_INSN:
                        supported = isSupportedInvoke((MethodInsnNode) node);
                        break;
                    default:
                        supported = false;
                }
                if (!supported) {
                    throw unsupported("Unsupported instruction for phase-two IR", instruction);
                }
            }
        }
    }

    private void validateLocalTypes(MethodNode method, MethodShape shape,
                                    CfgBuilder.Graph graph) {
        for (CfgBuilder.Block block : graph.getBlocks()) {
            for (CfgBuilder.Instruction instruction : block.getInstructions()) {
                AbstractInsnNode node = instruction.getNode();
                IrType requiredType = null;
                int local = -1;
                if (node instanceof VarInsnNode) {
                    int opcode = node.getOpcode();
                    if (opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE) {
                        requiredType = IrType.I32;
                    } else if (opcode == Opcodes.LLOAD || opcode == Opcodes.LSTORE) {
                        requiredType = IrType.I64;
                    } else if (opcode == Opcodes.FLOAD || opcode == Opcodes.FSTORE) {
                        requiredType = IrType.F32;
                    } else if (opcode == Opcodes.DLOAD || opcode == Opcodes.DSTORE) {
                        requiredType = IrType.F64;
                    } else if (opcode == Opcodes.ALOAD || opcode == Opcodes.ASTORE) {
                        requiredType = IrType.REFERENCE;
                    }
                    local = ((VarInsnNode) node).var;
                } else if (node instanceof IincInsnNode) {
                    requiredType = IrType.I32;
                    local = ((IincInsnNode) node).var;
                }
                if (requiredType == null) {
                    continue;
                }
                local = isWide(requiredType)
                        ? checkedWideLocal(local, method, instruction)
                        : checkedLocal(local, method, instruction);
                if (shape.wideContinuations[local]) {
                    throw unsupported("Local " + local
                            + " is the second slot of a category-two local", instruction);
                }
                IrType knownType = shape.localTypes[local];
                if (knownType == null) {
                    if (isWide(requiredType)
                            && (shape.localTypes[local + 1] != null
                            || shape.wideContinuations[local + 1])) {
                        throw unsupported("Category-two local overlaps an existing local at "
                                + (local + 1), instruction);
                    }
                    shape.localTypes[local] = requiredType;
                    if (isWide(requiredType)) {
                        shape.wideContinuations[local + 1] = true;
                    }
                } else if (knownType != requiredType) {
                    throw unsupported("Local " + local + " is " + knownType
                            + " but this instruction requires " + requiredType, instruction);
                } else if (isWide(requiredType)
                        && !shape.wideContinuations[local + 1]) {
                    throw unsupported("Category-two local is missing its second JVM slot",
                            instruction);
                }
            }
        }
    }

    private Map<CfgBuilder.Block, List<IrType>> computeStackTypes(
            CfgBuilder.Graph graph, Set<CfgBuilder.Block> reachable, MethodNode method,
            MethodShape shape) {
        Map<CfgBuilder.Block, List<IrType>> inputTypes = new HashMap<>();
        Queue<CfgBuilder.Block> work = new ArrayDeque<>();
        CfgBuilder.Block entry = graph.getBlocks().get(0);
        inputTypes.put(entry, Collections.<IrType>emptyList());
        work.add(entry);

        while (!work.isEmpty()) {
            CfgBuilder.Block block = work.remove();
            List<IrType> stack = new ArrayList<>(inputTypes.get(block));
            for (CfgBuilder.Instruction instruction : block.getInstructions()) {
                transferStackTypes(stack, instruction, method, shape);
            }
            for (CfgBuilder.Block successor : block.getSuccessors()) {
                if (!reachable.contains(successor)) {
                    continue;
                }
                mergeStackTypes(inputTypes, work, successor, stack, block);
            }
            for (CfgBuilder.ExceptionEdge edge : block.getExceptionEdges()) {
                if (reachable.contains(edge.getHandler())) {
                    mergeStackTypes(inputTypes, work, edge.getHandler(),
                            Collections.singletonList(IrType.REFERENCE), block);
                }
            }
        }
        return inputTypes;
    }

    private void mergeStackTypes(Map<CfgBuilder.Block, List<IrType>> inputTypes,
                                 Queue<CfgBuilder.Block> work,
                                 CfgBuilder.Block successor, List<IrType> stack,
                                 CfgBuilder.Block predecessor) {
        List<IrType> known = inputTypes.get(successor);
        if (known == null) {
            inputTypes.put(successor,
                    Collections.unmodifiableList(new ArrayList<>(stack)));
            work.add(successor);
        } else if (!known.equals(stack)) {
            CfgBuilder.Instruction last = predecessor.getInstructions().isEmpty() ? null
                    : predecessor.getInstructions().get(
                            predecessor.getInstructions().size() - 1);
            if (last == null) {
                throw new UnsupportedIrConstructException(
                        "Mismatched operand-stack types at CFG merge");
            }
            throw unsupported("Mismatched operand-stack types at CFG merge", last);
        }
    }

    private void transferStackTypes(List<IrType> stack, CfgBuilder.Instruction instruction,
                                    MethodNode method, MethodShape shape) {
        AbstractInsnNode node = instruction.getNode();
        int opcode = node.getOpcode();
        if (opcode == Opcodes.ACONST_NULL) {
            stack.add(IrType.REFERENCE);
        } else if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5
                || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH
                || opcode == Opcodes.ILOAD) {
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1
                || opcode == Opcodes.LLOAD) {
            stack.add(IrType.I64);
        } else if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2
                || opcode == Opcodes.FLOAD) {
            stack.add(IrType.F32);
        } else if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1
                || opcode == Opcodes.DLOAD) {
            stack.add(IrType.F64);
        } else if (node instanceof LdcInsnNode) {
            stack.add(constantType(((LdcInsnNode) node).cst));
        } else if (opcode == Opcodes.ALOAD) {
            int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
            IrType type = shape.localTypes[local];
            if (type != IrType.REFERENCE) {
                throw unsupported("ALOAD requires a reference local", instruction);
            }
            stack.add(type);
        } else if (opcode == Opcodes.ISTORE) {
            popType(stack, IrType.I32, instruction);
        } else if (opcode == Opcodes.LSTORE) {
            popType(stack, IrType.I64, instruction);
        } else if (opcode == Opcodes.FSTORE) {
            popType(stack, IrType.F32, instruction);
        } else if (opcode == Opcodes.DSTORE) {
            popType(stack, IrType.F64, instruction);
        } else if (opcode == Opcodes.ASTORE) {
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.DUP) {
            if (stack.isEmpty()) {
                throw unsupported("Operand stack underflow", instruction);
            }
            IrType top = stack.get(stack.size() - 1);
            if (top.getJvmSlots() != 1) {
                throw unsupported("DUP requires a category-one operand", instruction);
            }
            stack.add(top);
        } else if (opcode == Opcodes.POP) {
            if (stack.isEmpty()) {
                throw unsupported("Operand stack underflow", instruction);
            }
            IrType top = stack.remove(stack.size() - 1);
            if (top.getJvmSlots() != 1) {
                throw unsupported("POP requires a category-one operand", instruction);
            }
        } else if (opcode == Opcodes.SWAP) {
            if (stack.size() < 2) {
                throw unsupported("Operand stack underflow", instruction);
            }
            int topIndex = stack.size() - 1;
            int belowIndex = topIndex - 1;
            IrType top = stack.get(topIndex);
            IrType below = stack.get(belowIndex);
            if (top.getJvmSlots() != 1 || below.getJvmSlots() != 1) {
                throw unsupported("SWAP requires two category-one operands", instruction);
            }
            stack.set(belowIndex, top);
            stack.set(topIndex, below);
        } else if (isWideStackOperation(opcode)) {
            applyWideStackOperation(stack, opcode, instruction,
                    new ToIntFunction<IrType>() {
                        @Override
                        public int applyAsInt(IrType type) {
                            return type.getJvmSlots();
                        }
                    });
        } else if (isIntBinaryOp(opcode) || isIntDivRem(opcode)) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.I32);
        } else if (isLongBinaryOp(opcode)) {
            popType(stack, IrType.I64, instruction);
            popType(stack, IrType.I64, instruction);
            stack.add(IrType.I64);
        } else if (isFloatingBinaryOp(opcode)) {
            IrType type = floatingType(opcode);
            popType(stack, type, instruction);
            popType(stack, type, instruction);
            stack.add(type);
        } else if (isIntUnaryOp(opcode)) {
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.I32);
        } else if (isFloatingUnaryOp(opcode)) {
            IrType type = floatingType(opcode);
            popType(stack, type, instruction);
            stack.add(type);
        } else if (isFloatingCompareOp(opcode)) {
            IrType type = floatingType(opcode);
            popType(stack, type, instruction);
            popType(stack, type, instruction);
            stack.add(IrType.I32);
        } else if (isConversionOp(opcode)) {
            IrNodes.Conversion.Operation operation = conversionOperation(opcode);
            popType(stack, operation.getOperandType(), instruction);
            stack.add(operation.getResultType());
        } else if (opcode == Opcodes.NEW) {
            stack.add(IrType.REFERENCE);
        } else if (opcode == Opcodes.NEWARRAY) {
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.REFERENCE);
        } else if (opcode == Opcodes.ANEWARRAY) {
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.REFERENCE);
        } else if (opcode == Opcodes.CHECKCAST) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.REFERENCE);
        } else if (opcode == Opcodes.INSTANCEOF) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.ARRAYLENGTH) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.IALOAD) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.AALOAD) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.REFERENCE);
        } else if (opcode == Opcodes.IASTORE) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.AASTORE) {
            popType(stack, IrType.REFERENCE, instruction);
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.ATHROW) {
            popType(stack, IrType.REFERENCE, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("ATHROW requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.GETFIELD) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(fieldType(((FieldInsnNode) node).desc));
        } else if (opcode == Opcodes.PUTFIELD) {
            popType(stack, fieldType(((FieldInsnNode) node).desc), instruction);
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.GETSTATIC) {
            stack.add(fieldType(((FieldInsnNode) node).desc));
        } else if (opcode == Opcodes.PUTSTATIC) {
            popType(stack, fieldType(((FieldInsnNode) node).desc), instruction);
        } else if (node instanceof MethodInsnNode) {
            MethodInsnNode invoke = (MethodInsnNode) node;
            Type[] arguments = Type.getArgumentTypes(invoke.desc);
            for (int i = arguments.length - 1; i >= 0; i--) {
                popType(stack, irType(arguments[i]), instruction);
            }
            if (opcode != Opcodes.INVOKESTATIC) {
                popType(stack, IrType.REFERENCE, instruction);
            }
            Type returnType = Type.getReturnType(invoke.desc);
            if (returnType.getSort() != Type.VOID) {
                stack.add(irType(returnType));
            }
        } else if (isUnaryIntJump(opcode)) {
            popType(stack, IrType.I32, instruction);
        } else if (isBinaryIntJump(opcode)) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
        } else if (isReferenceNullJump(opcode)) {
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.TABLESWITCH || opcode == Opcodes.LOOKUPSWITCH) {
            popType(stack, IrType.I32, instruction);
        } else if (opcode == Opcodes.IRETURN) {
            popType(stack, IrType.I32, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("IRETURN requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.LRETURN) {
            popType(stack, IrType.I64, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("LRETURN requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.FRETURN) {
            popType(stack, IrType.F32, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("FRETURN requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.DRETURN) {
            popType(stack, IrType.F64, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("DRETURN requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.ARETURN) {
            popType(stack, IrType.REFERENCE, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("ARETURN requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.RETURN && !stack.isEmpty()) {
            throw unsupported("RETURN requires an empty operand stack", instruction);
        }
    }

    private void popType(List<IrType> stack, IrType expected,
                         CfgBuilder.Instruction instruction) {
        if (stack.isEmpty()) {
            throw unsupported("Operand stack underflow", instruction);
        }
        IrType actual = stack.remove(stack.size() - 1);
        if (actual != expected) {
            throw unsupported("Operand requires " + expected + " but stack contains " + actual,
                    instruction);
        }
    }

    private DefiniteLocals computeDefiniteLocals(MethodNode method, MethodShape shape,
                                                  CfgBuilder.Graph graph,
                                                  Set<CfgBuilder.Block> reachable) {
        Map<CfgBuilder.Block, boolean[]> in = new HashMap<>();
        Map<CfgBuilder.Block, boolean[]> out = new HashMap<>();
        CfgBuilder.Block entry = graph.getBlocks().get(0);

        for (CfgBuilder.Block block : reachable) {
            boolean[] values = new boolean[method.maxLocals];
            if (block == entry) {
                Arrays.fill(values, 0, shape.parameterLocalCount, true);
            } else {
                Arrays.fill(values, true);
            }
            in.put(block, values);
            out.put(block, transferDefined(block, values));
        }

        boolean changed;
        do {
            changed = false;
            for (CfgBuilder.Block block : graph.getBlocks()) {
                if (!reachable.contains(block) || block == entry) {
                    continue;
                }
                boolean[] merged = new boolean[method.maxLocals];
                Arrays.fill(merged, true);
                boolean foundPredecessor = false;
                for (CfgBuilder.Block predecessor : block.getPredecessors()) {
                    if (!reachable.contains(predecessor)) {
                        continue;
                    }
                    foundPredecessor = true;
                    boolean[] predecessorOut = out.get(predecessor);
                    for (int i = 0; i < merged.length; i++) {
                        merged[i] &= predecessorOut[i];
                    }
                }
                for (CfgBuilder.Block predecessor : block.getExceptionPredecessors()) {
                    if (!reachable.contains(predecessor)) {
                        continue;
                    }
                    foundPredecessor = true;
                    boolean[] predecessorOut = out.get(predecessor);
                    for (int i = 0; i < merged.length; i++) {
                        merged[i] &= predecessorOut[i];
                    }
                }
                if (!foundPredecessor) {
                    Arrays.fill(merged, false);
                }
                boolean[] nextOut = transferDefined(block, merged);
                if (!Arrays.equals(in.get(block), merged)) {
                    in.put(block, merged);
                    changed = true;
                }
                if (!Arrays.equals(out.get(block), nextOut)) {
                    out.put(block, nextOut);
                    changed = true;
                }
            }
        } while (changed);

        for (CfgBuilder.Block block : reachable) {
            boolean[] current = in.get(block).clone();
            for (CfgBuilder.Instruction instruction : block.getInstructions()) {
                AbstractInsnNode node = instruction.getNode();
                if (node instanceof VarInsnNode
                        && (node.getOpcode() == Opcodes.ILOAD
                        || node.getOpcode() == Opcodes.ALOAD
                        || node.getOpcode() == Opcodes.LLOAD
                        || node.getOpcode() == Opcodes.FLOAD
                        || node.getOpcode() == Opcodes.DLOAD)) {
                    boolean wide = node.getOpcode() == Opcodes.LLOAD
                            || node.getOpcode() == Opcodes.DLOAD;
                    int local = wide
                            ? checkedWideLocal(((VarInsnNode) node).var, method, instruction)
                            : checkedLocal(((VarInsnNode) node).var, method, instruction);
                    if (!current[local] || wide && !current[local + 1]) {
                        throw unsupported("Read of a local not defined on every incoming edge",
                                instruction);
                    }
                } else if (node instanceof VarInsnNode
                        && (node.getOpcode() == Opcodes.ISTORE
                        || node.getOpcode() == Opcodes.ASTORE
                        || node.getOpcode() == Opcodes.LSTORE
                        || node.getOpcode() == Opcodes.FSTORE
                        || node.getOpcode() == Opcodes.DSTORE)) {
                    boolean wide = node.getOpcode() == Opcodes.LSTORE
                            || node.getOpcode() == Opcodes.DSTORE;
                    int local = wide
                            ? checkedWideLocal(((VarInsnNode) node).var, method, instruction)
                            : checkedLocal(((VarInsnNode) node).var, method, instruction);
                    current[local] = true;
                    if (wide) {
                        current[local + 1] = true;
                    }
                } else if (node instanceof IincInsnNode) {
                    int local = checkedLocal(((IincInsnNode) node).var, method, instruction);
                    if (!current[local]) {
                        throw unsupported("IINC of a local not defined on every incoming edge",
                                instruction);
                    }
                }
            }
        }
        return new DefiniteLocals(in);
    }

    private boolean[] transferDefined(CfgBuilder.Block block, boolean[] input) {
        boolean[] result = input.clone();
        for (CfgBuilder.Instruction instruction : block.getInstructions()) {
            if (instruction.getNode() instanceof VarInsnNode
                    && (instruction.getNode().getOpcode() == Opcodes.ISTORE
                    || instruction.getNode().getOpcode() == Opcodes.ASTORE
                    || instruction.getNode().getOpcode() == Opcodes.LSTORE
                    || instruction.getNode().getOpcode() == Opcodes.FSTORE
                    || instruction.getNode().getOpcode() == Opcodes.DSTORE)) {
                int local = ((VarInsnNode) instruction.getNode()).var;
                if (local >= 0 && local < result.length) {
                    result[local] = true;
                    if ((instruction.getNode().getOpcode() == Opcodes.LSTORE
                            || instruction.getNode().getOpcode() == Opcodes.DSTORE)
                            && local + 1 < result.length) {
                        result[local + 1] = true;
                    }
                }
            }
        }
        return result;
    }

    private IrValue[] createParameters(IrMethod irMethod, MethodNode method, MethodShape shape) {
        IrValue[] locals = new IrValue[method.maxLocals];
        int local = 0;
        if (!shape.staticMethod) {
            locals[local++] = irMethod.addParameter(IrType.REFERENCE, "this", "obj");
        }
        Type[] arguments = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < arguments.length; i++) {
            locals[local] = irMethod.addParameter(irType(arguments[i]),
                    "arg" + i, "arg" + i);
            local += arguments[i].getSize();
        }
        return locals;
    }

    private ValueState lowerBlock(IrMethod irMethod, MethodNode method, MethodShape shape,
                                  CfgBuilder.Graph graph,
                                  CfgBuilder.Block rawBlock,
                                  Map<CfgBuilder.Block, IrBlock> irBlocks,
                                  BlockInputs inputs) {
        IrBlock block = irBlocks.get(rawBlock);
        ValueState state = new ValueState(inputs.locals.clone(), new ArrayList<>(inputs.stack));
        List<CfgBuilder.Instruction> instructions = rawBlock.getInstructions();

        if (instructions.isEmpty()) {
            if (shape.returnType != IrType.VOID) {
                throw new UnsupportedIrConstructException("Non-void method has an empty body");
            }
            block.setTerminator(new IrNodes.Return(null, -1));
            return state;
        }

        for (CfgBuilder.Instruction instruction : instructions) {
            AbstractInsnNode node = instruction.getNode();
            int opcode = node.getOpcode();
            if (opcode == Opcodes.NOP) {
                continue;
            }
            if (opcode == Opcodes.ACONST_NULL) {
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.NullReference(result,
                        instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                pushConstant(irMethod, block, state, opcode - Opcodes.ICONST_0,
                        instruction.getOriginalIndex());
            } else if (opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1) {
                pushLongConstant(irMethod, block, state,
                        opcode == Opcodes.LCONST_0 ? 0L : 1L,
                        instruction.getOriginalIndex());
            } else if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2) {
                pushFloatConstant(irMethod, block, state,
                        (float) (opcode - Opcodes.FCONST_0),
                        instruction.getOriginalIndex());
            } else if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1) {
                pushDoubleConstant(irMethod, block, state,
                        opcode == Opcodes.DCONST_0 ? 0.0d : 1.0d,
                        instruction.getOriginalIndex());
            } else if (node instanceof IntInsnNode
                    && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
                pushConstant(irMethod, block, state, ((IntInsnNode) node).operand,
                        instruction.getOriginalIndex());
            } else if (node instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode) node).cst;
                if (constant instanceof Integer) {
                    pushConstant(irMethod, block, state, (Integer) constant,
                            instruction.getOriginalIndex());
                } else if (constant instanceof Long) {
                    pushLongConstant(irMethod, block, state, (Long) constant,
                            instruction.getOriginalIndex());
                } else if (constant instanceof Float) {
                    pushFloatConstant(irMethod, block, state, (Float) constant,
                            instruction.getOriginalIndex());
                } else if (constant instanceof Double) {
                    pushDoubleConstant(irMethod, block, state, (Double) constant,
                            instruction.getOriginalIndex());
                } else if (constant instanceof String) {
                    pushStringConstant(irMethod, block, state, (String) constant,
                            instruction.getOriginalIndex());
                } else if (isSupportedClassConstant(constant)) {
                    pushClassConstant(irMethod, block, state, (Type) constant,
                            instruction.getOriginalIndex());
                } else {
                    throw unsupported("Unsupported LDC constant", instruction);
                }
            } else if (node instanceof VarInsnNode
                    && (opcode == Opcodes.ILOAD || opcode == Opcodes.LLOAD
                    || opcode == Opcodes.FLOAD || opcode == Opcodes.DLOAD
                    || opcode == Opcodes.ALOAD)) {
                int local = opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD
                        ? checkedWideLocal(((VarInsnNode) node).var, method, instruction)
                        : checkedLocal(((VarInsnNode) node).var, method, instruction);
                IrValue value = state.locals[local];
                IrType expected = opcode == Opcodes.ILOAD
                        ? IrType.I32 : opcode == Opcodes.LLOAD
                        ? IrType.I64 : opcode == Opcodes.FLOAD
                        ? IrType.F32 : opcode == Opcodes.DLOAD
                        ? IrType.F64 : IrType.REFERENCE;
                if (value == null || value.getType() != expected) {
                    String mnemonic = opcode == Opcodes.ILOAD ? "ILOAD"
                            : opcode == Opcodes.LLOAD ? "LLOAD"
                            : opcode == Opcodes.FLOAD ? "FLOAD"
                            : opcode == Opcodes.DLOAD ? "DLOAD" : "ALOAD";
                    throw unsupported(mnemonic
                            + " requires a defined " + expected + " local", instruction);
                }
                state.stack.add(value);
            } else if (node instanceof VarInsnNode && opcode == Opcodes.ISTORE) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.I32) {
                    throw unsupported("ISTORE cannot write a reference-typed local", instruction);
                }
                state.locals[local] = pop(state, IrType.I32, instruction);
            } else if (node instanceof VarInsnNode && opcode == Opcodes.LSTORE) {
                int local = checkedWideLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.I64
                        || !shape.wideContinuations[local + 1]) {
                    throw unsupported("LSTORE requires two i64 local slots", instruction);
                }
                state.locals[local] = pop(state, IrType.I64, instruction);
                state.locals[local + 1] = null;
            } else if (node instanceof VarInsnNode && opcode == Opcodes.FSTORE) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.F32) {
                    throw unsupported("FSTORE requires an f32 local", instruction);
                }
                state.locals[local] = pop(state, IrType.F32, instruction);
            } else if (node instanceof VarInsnNode && opcode == Opcodes.DSTORE) {
                int local = checkedWideLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.F64
                        || !shape.wideContinuations[local + 1]) {
                    throw unsupported("DSTORE requires two f64 local slots", instruction);
                }
                state.locals[local] = pop(state, IrType.F64, instruction);
                state.locals[local + 1] = null;
            } else if (node instanceof VarInsnNode && opcode == Opcodes.ASTORE) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.REFERENCE) {
                    throw unsupported("ASTORE cannot write an int-typed local", instruction);
                }
                state.locals[local] = pop(state, IrType.REFERENCE, instruction);
            } else if (node instanceof IincInsnNode) {
                IincInsnNode increment = (IincInsnNode) node;
                int local = checkedLocal(increment.var, method, instruction);
                IrValue oldValue = state.locals[local];
                if (oldValue == null || oldValue.getType() != IrType.I32) {
                    throw unsupported("IINC requires a defined i32 local", instruction);
                }
                IrValue amount = pushConstant(irMethod, block, null, increment.incr,
                        instruction.getOriginalIndex());
                IrValue result = blockBinary(irMethod, block, IrNodes.Binary.Operation.ADD,
                        oldValue, amount, instruction.getOriginalIndex());
                state.locals[local] = result;
            } else if (opcode == Opcodes.DUP) {
                IrValue value = pop(state, instruction);
                state.stack.add(value);
                state.stack.add(value);
            } else if (opcode == Opcodes.POP) {
                IrValue value = pop(state, instruction);
                if (value.getType().getJvmSlots() != 1) {
                    throw unsupported("POP requires a category-one operand", instruction);
                }
            } else if (opcode == Opcodes.SWAP) {
                if (state.stack.size() < 2) {
                    throw unsupported("Operand stack underflow", instruction);
                }
                int topIndex = state.stack.size() - 1;
                int belowIndex = topIndex - 1;
                IrValue top = state.stack.get(topIndex);
                IrValue below = state.stack.get(belowIndex);
                if (top.getType().getJvmSlots() != 1
                        || below.getType().getJvmSlots() != 1) {
                    throw unsupported("SWAP requires two category-one operands", instruction);
                }
                state.stack.set(belowIndex, top);
                state.stack.set(topIndex, below);
            } else if (isWideStackOperation(opcode)) {
                applyWideStackOperation(state.stack, opcode, instruction,
                        new ToIntFunction<IrValue>() {
                            @Override
                            public int applyAsInt(IrValue value) {
                                return value.getType().getJvmSlots();
                            }
                        });
            } else if (isIntBinaryOp(opcode)) {
                IrValue right = pop(state, IrType.I32, instruction);
                IrValue left = pop(state, IrType.I32, instruction);
                state.stack.add(blockBinary(irMethod, block, binaryOperation(opcode), left,
                        right, instruction.getOriginalIndex()));
            } else if (isLongBinaryOp(opcode)) {
                IrValue right = pop(state, IrType.I64, instruction);
                IrValue left = pop(state, IrType.I64, instruction);
                state.stack.add(blockLongBinary(irMethod, block, longBinaryOperation(opcode),
                        left, right, instruction.getOriginalIndex()));
            } else if (isFloatingBinaryOp(opcode)) {
                IrType type = floatingType(opcode);
                IrValue right = pop(state, type, instruction);
                IrValue left = pop(state, type, instruction);
                IrValue result = irMethod.newInstructionValue(type);
                block.addInstruction(new IrNodes.FloatingBinary(result,
                        floatingBinaryOperation(opcode), left, right,
                        instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (isIntDivRem(opcode)) {
                IrValue right = pop(state, IrType.I32, instruction);
                IrValue left = pop(state, IrType.I32, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.IntDivRem(result,
                        opcode == Opcodes.IDIV
                                ? IrNodes.IntDivRem.Operation.DIVIDE
                                : IrNodes.IntDivRem.Operation.REMAINDER,
                        left, right, instruction.getOriginalIndex(),
                        instruction.getSourceLine()));
                state.stack.add(result);
            } else if (isIntUnaryOp(opcode)) {
                IrValue operand = pop(state, IrType.I32, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.Unary(result, unaryOperation(opcode), operand,
                        instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (isFloatingUnaryOp(opcode)) {
                IrType type = floatingType(opcode);
                IrValue operand = pop(state, type, instruction);
                IrValue result = irMethod.newInstructionValue(type);
                block.addInstruction(new IrNodes.FloatingUnary(result, operand,
                        instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (isFloatingCompareOp(opcode)) {
                IrType type = floatingType(opcode);
                IrValue right = pop(state, type, instruction);
                IrValue left = pop(state, type, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                IrNodes.FloatingCompare.NanResult nanResult =
                        opcode == Opcodes.FCMPL || opcode == Opcodes.DCMPL
                                ? IrNodes.FloatingCompare.NanResult.LESS
                                : IrNodes.FloatingCompare.NanResult.GREATER;
                block.addInstruction(new IrNodes.FloatingCompare(result, left, right,
                        nanResult, instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (isConversionOp(opcode)) {
                IrNodes.Conversion.Operation operation = conversionOperation(opcode);
                IrValue operand = pop(state, operation.getOperandType(), instruction);
                IrValue result = irMethod.newInstructionValue(operation.getResultType());
                block.addInstruction(new IrNodes.Conversion(result, operation, operand,
                        instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (node instanceof TypeInsnNode && opcode == Opcodes.NEW) {
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.NewObject(result,
                        ((TypeInsnNode) node).desc, instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (opcode == Opcodes.NEWARRAY) {
                IrValue length = pop(state, IrType.I32, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.NewArray(result, length,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                state.stack.add(result);
            } else if (node instanceof TypeInsnNode && opcode == Opcodes.ANEWARRAY) {
                IrValue length = pop(state, IrType.I32, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.NewObjectArray(result, length,
                        ((TypeInsnNode) node).desc, instruction.getOriginalIndex(),
                        instruction.getSourceLine()));
                state.stack.add(result);
            } else if (node instanceof TypeInsnNode && opcode == Opcodes.CHECKCAST) {
                IrValue operand = pop(state, IrType.REFERENCE, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.CheckCast(result, operand,
                        ((TypeInsnNode) node).desc, instruction.getOriginalIndex(),
                        instruction.getSourceLine()));
                state.stack.add(result);
            } else if (node instanceof TypeInsnNode && opcode == Opcodes.INSTANCEOF) {
                IrValue operand = pop(state, IrType.REFERENCE, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.InstanceOf(result, operand,
                        ((TypeInsnNode) node).desc, instruction.getOriginalIndex()));
                state.stack.add(result);
            } else if (opcode == Opcodes.ARRAYLENGTH) {
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.ArrayLength(result, array,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                state.stack.add(result);
            } else if (opcode == Opcodes.IALOAD) {
                IrValue index = pop(state, IrType.I32, instruction);
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.ArrayLoad(result, array, index,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                state.stack.add(result);
            } else if (opcode == Opcodes.AALOAD) {
                IrValue index = pop(state, IrType.I32, instruction);
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.REFERENCE);
                block.addInstruction(new IrNodes.ArrayLoad(result, array, index,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                state.stack.add(result);
            } else if (opcode == Opcodes.IASTORE) {
                IrValue value = pop(state, IrType.I32, instruction);
                IrValue index = pop(state, IrType.I32, instruction);
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                block.addInstruction(new IrNodes.ArrayStore(array, index, value,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
            } else if (opcode == Opcodes.AASTORE) {
                IrValue value = pop(state, IrType.REFERENCE, instruction);
                IrValue index = pop(state, IrType.I32, instruction);
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                block.addInstruction(new IrNodes.ArrayStore(array, index, value,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
            } else if (node instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) node;
                IrType fieldType = fieldType(field.desc);
                if (opcode == Opcodes.GETFIELD) {
                    IrValue receiver = pop(state, IrType.REFERENCE, instruction);
                    IrValue result = irMethod.newInstructionValue(fieldType);
                    block.addInstruction(new IrNodes.GetField(result, field.owner, field.name,
                            field.desc, receiver, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
                    state.stack.add(result);
                } else if (opcode == Opcodes.PUTFIELD) {
                    IrValue value = pop(state, fieldType, instruction);
                    IrValue receiver = pop(state, IrType.REFERENCE, instruction);
                    block.addInstruction(new IrNodes.PutField(field.owner, field.name,
                            field.desc, receiver, value, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
                } else if (opcode == Opcodes.GETSTATIC) {
                    IrValue result = irMethod.newInstructionValue(fieldType);
                    block.addInstruction(new IrNodes.GetStaticField(result, field.owner,
                            field.name, field.desc, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
                    state.stack.add(result);
                } else if (opcode == Opcodes.PUTSTATIC) {
                    IrValue value = pop(state, fieldType, instruction);
                    block.addInstruction(new IrNodes.PutStaticField(field.owner, field.name,
                            field.desc, value, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
                } else {
                    throw unsupported("Unsupported field instruction", instruction);
                }
            } else if (node instanceof MethodInsnNode) {
                MethodInsnNode invoke = (MethodInsnNode) node;
                if (isStringLength(invoke)) {
                    IrValue receiver = pop(state, IrType.REFERENCE, instruction);
                    IrValue result = irMethod.newInstructionValue(IrType.I32);
                    block.addInstruction(new IrNodes.StringLength(result, receiver,
                            instruction.getOriginalIndex(), instruction.getSourceLine()));
                    state.stack.add(result);
                    continue;
                }
                Type[] argumentTypes = Type.getArgumentTypes(invoke.desc);
                List<IrValue> arguments = new ArrayList<>(
                        Collections.nCopies(argumentTypes.length, (IrValue) null));
                for (int i = argumentTypes.length - 1; i >= 0; i--) {
                    arguments.set(i, pop(state, irType(argumentTypes[i]), instruction));
                }
                IrValue receiver = opcode != Opcodes.INVOKESTATIC
                        ? pop(state, IrType.REFERENCE, instruction) : null;
                Type returnType = Type.getReturnType(invoke.desc);
                IrValue result = returnType.getSort() == Type.VOID ? null
                        : irMethod.newInstructionValue(irType(returnType));
                block.addInstruction(new IrNodes.Invoke(result, invokeKind(opcode),
                        invoke.owner, invoke.name, invoke.desc, receiver, arguments,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                if (result != null) {
                    state.stack.add(result);
                }
            } else if (node instanceof JumpInsnNode) {
                lowerJump(instruction, (JumpInsnNode) node, graph, rawBlock, block, state,
                        irBlocks);
            } else if (node instanceof TableSwitchInsnNode
                    || node instanceof LookupSwitchInsnNode) {
                lowerSwitch(instruction, graph, block, state, irBlocks);
            } else if (opcode == Opcodes.IRETURN) {
                if (shape.returnType != IrType.I32) {
                    throw unsupported("IRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, IrType.I32, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.LRETURN) {
                if (shape.returnType != IrType.I64) {
                    throw unsupported("LRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, IrType.I64, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.FRETURN) {
                if (shape.returnType != IrType.F32) {
                    throw unsupported("FRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, IrType.F32, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.DRETURN) {
                if (shape.returnType != IrType.F64) {
                    throw unsupported("DRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, IrType.F64, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.ARETURN) {
                if (shape.returnType != IrType.REFERENCE) {
                    throw unsupported("ARETURN does not match the method descriptor",
                            instruction);
                }
                block.setTerminator(new IrNodes.Return(
                        pop(state, IrType.REFERENCE, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.RETURN) {
                if (shape.returnType != IrType.VOID) {
                    throw unsupported("RETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(null, instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.ATHROW) {
                block.setTerminator(new IrNodes.Throw(
                        pop(state, IrType.REFERENCE, instruction),
                        instruction.getOriginalIndex()));
            }
        }

        if (block.getTerminator() == null) {
            if (rawBlock.getSuccessors().size() == 1) {
                block.setTerminator(new IrNodes.Goto(irBlocks.get(rawBlock.getSuccessors().get(0)),
                        instructions.get(instructions.size() - 1).getOriginalIndex()));
            } else {
                CfgBuilder.Instruction last = instructions.get(instructions.size() - 1);
                throw unsupported("Method can fall off the end without a return", last);
            }
        }
        return state;
    }

    private void lowerJump(CfgBuilder.Instruction instruction, JumpInsnNode jump,
                           CfgBuilder.Graph graph, CfgBuilder.Block rawBlock, IrBlock block,
                           ValueState state, Map<CfgBuilder.Block, IrBlock> irBlocks) {
        CfgBuilder.Block target = graph.getBlock(jump.label);
        if (jump.getOpcode() == Opcodes.GOTO) {
            block.setTerminator(new IrNodes.Goto(irBlocks.get(target),
                    instruction.getOriginalIndex()));
            return;
        }

        if (rawBlock.getSuccessors().size() != 2) {
            throw unsupported("Conditional branch does not have two successors", instruction);
        }
        if (isReferenceNullJump(jump.getOpcode())) {
            IrValue reference = pop(state, IrType.REFERENCE, instruction);
            IrNodes.ReferenceBranch.Condition condition =
                    jump.getOpcode() == Opcodes.IFNULL
                            ? IrNodes.ReferenceBranch.Condition.IS_NULL
                            : IrNodes.ReferenceBranch.Condition.IS_NON_NULL;
            block.setTerminator(new IrNodes.ReferenceBranch(condition, reference,
                    irBlocks.get(target), irBlocks.get(rawBlock.getSuccessors().get(1)),
                    instruction.getOriginalIndex()));
            return;
        }

        IrValue right = null;
        IrValue left;
        if (isBinaryIntJump(jump.getOpcode())) {
            right = pop(state, IrType.I32, instruction);
            left = pop(state, IrType.I32, instruction);
        } else {
            left = pop(state, IrType.I32, instruction);
        }
        block.setTerminator(new IrNodes.Branch(condition(jump.getOpcode()), left, right,
                irBlocks.get(target), irBlocks.get(rawBlock.getSuccessors().get(1)),
                instruction.getOriginalIndex()));
    }

    private void lowerSwitch(CfgBuilder.Instruction instruction, CfgBuilder.Graph graph,
                             IrBlock block, ValueState state,
                             Map<CfgBuilder.Block, IrBlock> irBlocks) {
        IrValue selector = pop(state, IrType.I32, instruction);
        List<Integer> keys = new ArrayList<>();
        List<IrBlock> targets = new ArrayList<>();
        LabelNode defaultLabel;

        if (instruction.getNode() instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) instruction.getNode();
            for (int i = 0; i < tableSwitch.labels.size(); i++) {
                keys.add((int) ((long) tableSwitch.min + i));
                targets.add(irBlocks.get(graph.getBlock(tableSwitch.labels.get(i))));
            }
            defaultLabel = tableSwitch.dflt;
        } else {
            LookupSwitchInsnNode lookupSwitch =
                    (LookupSwitchInsnNode) instruction.getNode();
            keys.addAll(lookupSwitch.keys);
            for (LabelNode label : lookupSwitch.labels) {
                targets.add(irBlocks.get(graph.getBlock(label)));
            }
            defaultLabel = lookupSwitch.dflt;
        }

        IrBlock defaultTarget = irBlocks.get(graph.getBlock(defaultLabel));
        if (targets.contains(null) || defaultTarget == null) {
            throw unsupported("Switch target is unreachable or missing", instruction);
        }
        block.setTerminator(new IrNodes.Switch(selector, keys, targets, defaultTarget,
                instruction.getOriginalIndex()));
    }

    private void connectPhis(CfgBuilder.Graph graph, Set<CfgBuilder.Block> reachable,
                             Map<CfgBuilder.Block, BlockInputs> inputs,
                             Map<CfgBuilder.Block, ValueState> outputs,
                             Map<CfgBuilder.Block, IrBlock> irBlocks) {
        for (CfgBuilder.Block predecessor : graph.getBlocks()) {
            if (!reachable.contains(predecessor)) {
                continue;
            }
            ValueState output = outputs.get(predecessor);
            for (CfgBuilder.Block successor : predecessor.getSuccessors()) {
                if (!reachable.contains(successor)) {
                    continue;
                }
                BlockInputs destination = inputs.get(successor);
                connectLocalPhis(predecessor, output, destination, irBlocks);
                if (destination.stackPhis.length != output.stack.size()) {
                    throw new IllegalStateException("Stack shape changed after analysis");
                }
                for (int slot = 0; slot < destination.stackPhis.length; slot++) {
                    if (output.stack.get(slot).getType()
                            != destination.stackPhis[slot].getResult().getType()) {
                        throw new UnsupportedIrConstructException(
                                "Operand stack changes carrier type across a CFG edge");
                    }
                    destination.stackPhis[slot].addIncoming(irBlocks.get(predecessor),
                            output.stack.get(slot));
                }
            }
            for (CfgBuilder.ExceptionEdge edge : predecessor.getExceptionEdges()) {
                if (reachable.contains(edge.getHandler())) {
                    connectLocalPhis(predecessor, output, inputs.get(edge.getHandler()),
                            irBlocks);
                }
            }
        }
    }

    private void connectLocalPhis(CfgBuilder.Block predecessor, ValueState output,
                                  BlockInputs destination,
                                  Map<CfgBuilder.Block, IrBlock> irBlocks) {
        for (int local = 0; local < destination.localPhis.length; local++) {
            IrPhi phi = destination.localPhis[local];
            if (phi == null) {
                continue;
            }
            if (output.locals[local] == null) {
                throw new IllegalStateException("Missing value for definite local " + local);
            }
            if (output.locals[local].getType() != phi.getResult().getType()) {
                throw new UnsupportedIrConstructException(
                        "Local changes carrier type across a CFG edge");
            }
            phi.addIncoming(irBlocks.get(predecessor), output.locals[local]);
        }
    }

    private IrValue pushConstant(IrMethod method, IrBlock block, ValueState state, int value,
                                 int offset) {
        IrValue result = method.newInstructionValue(IrType.I32);
        block.addInstruction(new IrNodes.Const(result, value, offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue pushLongConstant(IrMethod method, IrBlock block, ValueState state,
                                     long value, int offset) {
        IrValue result = method.newInstructionValue(IrType.I64);
        block.addInstruction(new IrNodes.LongConst(result, value, offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue pushFloatConstant(IrMethod method, IrBlock block, ValueState state,
                                      float value, int offset) {
        IrValue result = method.newInstructionValue(IrType.F32);
        block.addInstruction(new IrNodes.FloatConst(result,
                Float.floatToRawIntBits(value), offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue pushDoubleConstant(IrMethod method, IrBlock block, ValueState state,
                                       double value, int offset) {
        IrValue result = method.newInstructionValue(IrType.F64);
        block.addInstruction(new IrNodes.DoubleConst(result,
                Double.doubleToRawLongBits(value), offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue pushStringConstant(IrMethod method, IrBlock block, ValueState state,
                                       String value, int offset) {
        IrValue result = method.newInstructionValue(IrType.REFERENCE);
        block.addInstruction(new IrNodes.StringConst(result, value, offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue pushClassConstant(IrMethod method, IrBlock block, ValueState state,
                                      Type type, int offset) {
        IrValue result = method.newInstructionValue(IrType.REFERENCE);
        String className = type.getSort() == Type.OBJECT
                ? type.getInternalName() : type.getDescriptor();
        block.addInstruction(new IrNodes.ClassConst(result, className, offset));
        if (state != null) {
            state.stack.add(result);
        }
        return result;
    }

    private IrValue blockBinary(IrMethod method, IrBlock block,
                                IrNodes.Binary.Operation operation,
                                IrValue left, IrValue right, int offset) {
        IrValue result = method.newInstructionValue(IrType.I32);
        block.addInstruction(new IrNodes.Binary(result, operation, left, right, offset));
        return result;
    }

    private IrValue blockLongBinary(IrMethod method, IrBlock block,
                                    IrNodes.LongBinary.Operation operation,
                                    IrValue left, IrValue right, int offset) {
        IrValue result = method.newInstructionValue(IrType.I64);
        block.addInstruction(new IrNodes.LongBinary(result, operation, left, right, offset));
        return result;
    }

    private IrValue pop(ValueState state, CfgBuilder.Instruction instruction) {
        if (state.stack.isEmpty()) {
            throw unsupported("Operand stack underflow", instruction);
        }
        return state.stack.remove(state.stack.size() - 1);
    }

    private IrValue pop(ValueState state, IrType expected,
                        CfgBuilder.Instruction instruction) {
        IrValue value = pop(state, instruction);
        if (value.getType() != expected) {
            throw unsupported("Operand requires " + expected + " but stack contains "
                    + value.getType(), instruction);
        }
        return value;
    }

    /**
     * Applies category-aware stack-only operations in both the type pass and
     * value lowering. All operands are validated before the first mutation.
     */
    private static <T> void applyWideStackOperation(
            List<T> stack, int opcode, CfgBuilder.Instruction instruction,
            ToIntFunction<T> slots) {
        int size = stack.size();
        if (size < 1) {
            throw unsupported("Operand stack underflow", instruction);
        }
        T value1 = stack.get(size - 1);
        int category1 = slots.applyAsInt(value1);

        if (opcode == Opcodes.DUP2) {
            if (category1 == 2) {
                stack.add(value1);
                return;
            }
            if (category1 == 1 && size >= 2) {
                T value2 = stack.get(size - 2);
                if (slots.applyAsInt(value2) == 1) {
                    stack.add(value2);
                    stack.add(value1);
                    return;
                }
            }
            throw illegalWideStackForm("DUP2", instruction);
        }

        if (opcode == Opcodes.DUP_X2) {
            if (category1 != 1) {
                throw illegalWideStackForm("DUP_X2", instruction);
            }
            if (size < 2) {
                throw unsupported("Operand stack underflow", instruction);
            }
            T value2 = stack.get(size - 2);
            int category2 = slots.applyAsInt(value2);
            if (category2 == 2) {
                stack.add(size - 2, value1);
                return;
            }
            if (category2 == 1 && size >= 3) {
                T value3 = stack.get(size - 3);
                if (slots.applyAsInt(value3) == 1) {
                    stack.add(size - 3, value1);
                    return;
                }
            }
            throw illegalWideStackForm("DUP_X2", instruction);
        }

        if (opcode == Opcodes.DUP2_X1) {
            if (category1 == 2) {
                if (size >= 2) {
                    T value2 = stack.get(size - 2);
                    if (slots.applyAsInt(value2) == 1) {
                        stack.add(size - 2, value1);
                        return;
                    }
                }
                throw illegalWideStackForm("DUP2_X1", instruction);
            }
            if (category1 == 1 && size >= 3) {
                T value2 = stack.get(size - 2);
                T value3 = stack.get(size - 3);
                if (slots.applyAsInt(value2) == 1
                        && slots.applyAsInt(value3) == 1) {
                    stack.add(size - 3, value2);
                    stack.add(size - 2, value1);
                    return;
                }
            }
            throw illegalWideStackForm("DUP2_X1", instruction);
        }

        if (opcode == Opcodes.DUP2_X2) {
            if (category1 == 2) {
                if (size < 2) {
                    throw unsupported("Operand stack underflow", instruction);
                }
                T value2 = stack.get(size - 2);
                int category2 = slots.applyAsInt(value2);
                if (category2 == 2) {
                    stack.add(size - 2, value1);
                    return;
                }
                if (category2 == 1 && size >= 3) {
                    T value3 = stack.get(size - 3);
                    if (slots.applyAsInt(value3) == 1) {
                        stack.add(size - 3, value1);
                        return;
                    }
                }
                throw illegalWideStackForm("DUP2_X2", instruction);
            }
            if (category1 == 1 && size >= 3) {
                T value2 = stack.get(size - 2);
                T value3 = stack.get(size - 3);
                if (slots.applyAsInt(value2) != 1) {
                    throw illegalWideStackForm("DUP2_X2", instruction);
                }
                int category3 = slots.applyAsInt(value3);
                if (category3 == 2) {
                    stack.add(size - 3, value2);
                    stack.add(size - 2, value1);
                    return;
                }
                if (category3 == 1 && size >= 4) {
                    T value4 = stack.get(size - 4);
                    if (slots.applyAsInt(value4) == 1) {
                        stack.add(size - 4, value2);
                        stack.add(size - 3, value1);
                        return;
                    }
                }
            }
            throw illegalWideStackForm("DUP2_X2", instruction);
        }

        if (opcode == Opcodes.POP2) {
            if (category1 == 2) {
                stack.remove(size - 1);
                return;
            }
            if (category1 == 1 && size >= 2
                    && slots.applyAsInt(stack.get(size - 2)) == 1) {
                stack.remove(size - 1);
                stack.remove(size - 2);
                return;
            }
            throw illegalWideStackForm("POP2", instruction);
        }

        throw new IllegalArgumentException("Not a wide stack operation: " + opcode);
    }

    private static UnsupportedIrConstructException illegalWideStackForm(
            String mnemonic, CfgBuilder.Instruction instruction) {
        return unsupported(mnemonic + " operand categories do not match a legal JVM form",
                instruction);
    }

    private int checkedLocal(int local, MethodNode method, CfgBuilder.Instruction instruction) {
        if (local < 0 || local >= method.maxLocals) {
            throw unsupported("Local index is outside maxLocals", instruction);
        }
        return local;
    }

    private int checkedWideLocal(int local, MethodNode method,
                                 CfgBuilder.Instruction instruction) {
        if (local < 0 || local + 1 >= method.maxLocals) {
            throw unsupported("Wide local index is outside maxLocals", instruction);
        }
        return local;
    }

    private static boolean isIntLike(Type type) {
        int sort = type.getSort();
        return sort >= Type.BOOLEAN && sort <= Type.INT;
    }

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static IrType irType(Type type) {
        if (isIntLike(type)) {
            return IrType.I32;
        }
        if (type.getSort() == Type.LONG) {
            return IrType.I64;
        }
        if (type.getSort() == Type.FLOAT) {
            return IrType.F32;
        }
        if (type.getSort() == Type.DOUBLE) {
            return IrType.F64;
        }
        if (isReference(type)) {
            return IrType.REFERENCE;
        }
        throw new IllegalArgumentException("Unsupported JVM carrier type " + type);
    }

    private static IrType constantType(Object constant) {
        if (constant instanceof Integer) {
            return IrType.I32;
        }
        if (constant instanceof Long) {
            return IrType.I64;
        }
        if (constant instanceof Float) {
            return IrType.F32;
        }
        if (constant instanceof Double) {
            return IrType.F64;
        }
        if (constant instanceof String || isSupportedClassConstant(constant)) {
            return IrType.REFERENCE;
        }
        throw new IllegalArgumentException("Unsupported LDC constant " + constant);
    }

    private static boolean isSupportedClassConstant(Object constant) {
        return constant instanceof Type && isReference((Type) constant);
    }

    private static boolean isSupportedFieldDescriptor(String descriptor) {
        try {
            fieldType(descriptor);
            return true;
        } catch (IllegalArgumentException malformedOrUnsupportedDescriptor) {
            return false;
        }
    }

    private static IrType fieldType(String descriptor) {
        Type type = Type.getType(descriptor);
        if (isIntLike(type)) {
            return IrType.I32;
        }
        if (type.getSort() == Type.LONG) {
            return IrType.I64;
        }
        if (type.getSort() == Type.FLOAT) {
            return IrType.F32;
        }
        if (type.getSort() == Type.DOUBLE) {
            return IrType.F64;
        }
        if (isReference(type)) {
            return IrType.REFERENCE;
        }
        throw new IllegalArgumentException("Unsupported field descriptor " + descriptor);
    }

    private static boolean isSupportedInvoke(MethodInsnNode invoke) {
        int opcode = invoke.getOpcode();
        if (opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEVIRTUAL
                && opcode != Opcodes.INVOKEINTERFACE
                && opcode != Opcodes.INVOKESPECIAL) {
            return false;
        }
        if (invoke.owner == null || invoke.owner.isEmpty()
                || invoke.name == null || invoke.name.isEmpty()
                || invoke.desc == null || invoke.desc.isEmpty()) {
            return false;
        }
        try {
            Type returnType = Type.getReturnType(invoke.desc);
            if ("<init>".equals(invoke.name)) {
                if (opcode != Opcodes.INVOKESPECIAL
                        || returnType.getSort() != Type.VOID) {
                    return false;
                }
            } else if (!isSupportedInvokeReturn(returnType)) {
                return false;
            }
            for (Type argument : Type.getArgumentTypes(invoke.desc)) {
                if (!isIntLike(argument)
                        && argument.getSort() != Type.LONG
                        && argument.getSort() != Type.FLOAT
                        && argument.getSort() != Type.DOUBLE
                        && !isReference(argument)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException malformedDescriptor) {
            return false;
        }
    }

    private static boolean isSupportedInvokeReturn(Type type) {
        int sort = type.getSort();
        return sort == Type.VOID || isIntLike(type) || sort == Type.LONG
                || sort == Type.FLOAT || sort == Type.DOUBLE
                || isReference(type);
    }

    private static IrNodes.Invoke.Kind invokeKind(int opcode) {
        switch (opcode) {
            case Opcodes.INVOKESTATIC:
                return IrNodes.Invoke.Kind.STATIC;
            case Opcodes.INVOKEVIRTUAL:
                return IrNodes.Invoke.Kind.VIRTUAL;
            case Opcodes.INVOKEINTERFACE:
                return IrNodes.Invoke.Kind.INTERFACE;
            case Opcodes.INVOKESPECIAL:
                return IrNodes.Invoke.Kind.SPECIAL;
            default:
                throw new IllegalArgumentException("Not a supported invoke opcode: " + opcode);
        }
    }

    private static boolean isWide(IrType type) {
        return type == IrType.I64 || type == IrType.F64;
    }

    private static boolean isWideStackOperation(int opcode) {
        return opcode == Opcodes.POP2 || opcode == Opcodes.DUP_X2
                || opcode == Opcodes.DUP2 || opcode == Opcodes.DUP2_X1
                || opcode == Opcodes.DUP2_X2;
    }

    private static boolean isFloatingBinaryOp(int opcode) {
        return opcode == Opcodes.FADD || opcode == Opcodes.DADD
                || opcode == Opcodes.FSUB || opcode == Opcodes.DSUB
                || opcode == Opcodes.FMUL || opcode == Opcodes.DMUL
                || opcode == Opcodes.FDIV || opcode == Opcodes.DDIV
                || opcode == Opcodes.FREM || opcode == Opcodes.DREM;
    }

    private static boolean isFloatingUnaryOp(int opcode) {
        return opcode == Opcodes.FNEG || opcode == Opcodes.DNEG;
    }

    private static boolean isFloatingCompareOp(int opcode) {
        return opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG
                || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG;
    }

    private static boolean isConversionOp(int opcode) {
        return opcode == Opcodes.I2L || opcode == Opcodes.L2I
                || opcode == Opcodes.I2F || opcode == Opcodes.F2I
                || opcode == Opcodes.L2F || opcode == Opcodes.F2L
                || opcode == Opcodes.I2D || opcode == Opcodes.D2I
                || opcode == Opcodes.L2D || opcode == Opcodes.D2L
                || opcode == Opcodes.F2D || opcode == Opcodes.D2F;
    }

    private static IrType floatingType(int opcode) {
        switch (opcode) {
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM:
            case Opcodes.FNEG:
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
                return IrType.F32;
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM:
            case Opcodes.DNEG:
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                return IrType.F64;
            default:
                throw new IllegalArgumentException("Not a floating opcode: " + opcode);
        }
    }

    private static IrNodes.FloatingBinary.Operation floatingBinaryOperation(int opcode) {
        switch (opcode) {
            case Opcodes.FADD:
            case Opcodes.DADD:
                return IrNodes.FloatingBinary.Operation.ADD;
            case Opcodes.FSUB:
            case Opcodes.DSUB:
                return IrNodes.FloatingBinary.Operation.SUBTRACT;
            case Opcodes.FMUL:
            case Opcodes.DMUL:
                return IrNodes.FloatingBinary.Operation.MULTIPLY;
            case Opcodes.FDIV:
            case Opcodes.DDIV:
                return IrNodes.FloatingBinary.Operation.DIVIDE;
            case Opcodes.FREM:
            case Opcodes.DREM:
                return IrNodes.FloatingBinary.Operation.REMAINDER;
            default:
                throw new IllegalArgumentException(
                        "Not a floating binary opcode: " + opcode);
        }
    }

    private static IrNodes.Conversion.Operation conversionOperation(int opcode) {
        switch (opcode) {
            case Opcodes.I2L:
                return IrNodes.Conversion.Operation.I2L;
            case Opcodes.L2I:
                return IrNodes.Conversion.Operation.L2I;
            case Opcodes.I2F:
                return IrNodes.Conversion.Operation.I2F;
            case Opcodes.F2I:
                return IrNodes.Conversion.Operation.F2I;
            case Opcodes.L2F:
                return IrNodes.Conversion.Operation.L2F;
            case Opcodes.F2L:
                return IrNodes.Conversion.Operation.F2L;
            case Opcodes.I2D:
                return IrNodes.Conversion.Operation.I2D;
            case Opcodes.D2I:
                return IrNodes.Conversion.Operation.D2I;
            case Opcodes.L2D:
                return IrNodes.Conversion.Operation.L2D;
            case Opcodes.D2L:
                return IrNodes.Conversion.Operation.D2L;
            case Opcodes.F2D:
                return IrNodes.Conversion.Operation.F2D;
            case Opcodes.D2F:
                return IrNodes.Conversion.Operation.D2F;
            default:
                throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        }
    }

    private static boolean isIntBinaryOp(int opcode) {
        return opcode == Opcodes.IADD || opcode == Opcodes.ISUB || opcode == Opcodes.IMUL
                || opcode == Opcodes.IAND || opcode == Opcodes.IOR || opcode == Opcodes.IXOR
                || opcode == Opcodes.ISHL || opcode == Opcodes.ISHR
                || opcode == Opcodes.IUSHR;
    }

    private static boolean isIntDivRem(int opcode) {
        return opcode == Opcodes.IDIV || opcode == Opcodes.IREM;
    }

    private static boolean isLongBinaryOp(int opcode) {
        return opcode == Opcodes.LADD || opcode == Opcodes.LSUB || opcode == Opcodes.LMUL;
    }

    private static boolean isIntUnaryOp(int opcode) {
        return opcode == Opcodes.INEG || opcode == Opcodes.I2B || opcode == Opcodes.I2S
                || opcode == Opcodes.I2C;
    }

    private static IrNodes.Binary.Operation binaryOperation(int opcode) {
        switch (opcode) {
            case Opcodes.IADD:
                return IrNodes.Binary.Operation.ADD;
            case Opcodes.ISUB:
                return IrNodes.Binary.Operation.SUBTRACT;
            case Opcodes.IMUL:
                return IrNodes.Binary.Operation.MULTIPLY;
            case Opcodes.IAND:
                return IrNodes.Binary.Operation.AND;
            case Opcodes.IOR:
                return IrNodes.Binary.Operation.OR;
            case Opcodes.IXOR:
                return IrNodes.Binary.Operation.XOR;
            case Opcodes.ISHL:
                return IrNodes.Binary.Operation.SHL;
            case Opcodes.ISHR:
                return IrNodes.Binary.Operation.SHR;
            case Opcodes.IUSHR:
                return IrNodes.Binary.Operation.USHR;
            default:
                throw new IllegalArgumentException("Not an integer binary opcode: " + opcode);
        }
    }

    private static IrNodes.LongBinary.Operation longBinaryOperation(int opcode) {
        switch (opcode) {
            case Opcodes.LADD:
                return IrNodes.LongBinary.Operation.ADD;
            case Opcodes.LSUB:
                return IrNodes.LongBinary.Operation.SUBTRACT;
            case Opcodes.LMUL:
                return IrNodes.LongBinary.Operation.MULTIPLY;
            default:
                throw new IllegalArgumentException("Not a long binary opcode: " + opcode);
        }
    }

    private static IrNodes.Unary.Operation unaryOperation(int opcode) {
        switch (opcode) {
            case Opcodes.INEG:
                return IrNodes.Unary.Operation.NEGATE;
            case Opcodes.I2B:
                return IrNodes.Unary.Operation.I2B;
            case Opcodes.I2S:
                return IrNodes.Unary.Operation.I2S;
            case Opcodes.I2C:
                return IrNodes.Unary.Operation.I2C;
            default:
                throw new IllegalArgumentException("Not an integer unary opcode: " + opcode);
        }
    }

    private static boolean isStringLength(MethodInsnNode invoke) {
        return invoke.getOpcode() == Opcodes.INVOKEVIRTUAL
                && "java/lang/String".equals(invoke.owner)
                && "length".equals(invoke.name)
                && "()I".equals(invoke.desc);
    }

    private static boolean isUnaryIntJump(int opcode) {
        return opcode >= Opcodes.IFEQ && opcode <= Opcodes.IFLE;
    }

    private static boolean isBinaryIntJump(int opcode) {
        return opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ICMPLE;
    }

    private static boolean isReferenceNullJump(int opcode) {
        return opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL;
    }

    private static IrNodes.Branch.Condition condition(int opcode) {
        switch (opcode) {
            case Opcodes.IFEQ:
            case Opcodes.IF_ICMPEQ:
                return IrNodes.Branch.Condition.EQ;
            case Opcodes.IFNE:
            case Opcodes.IF_ICMPNE:
                return IrNodes.Branch.Condition.NE;
            case Opcodes.IFLT:
            case Opcodes.IF_ICMPLT:
                return IrNodes.Branch.Condition.LT;
            case Opcodes.IFGE:
            case Opcodes.IF_ICMPGE:
                return IrNodes.Branch.Condition.GE;
            case Opcodes.IFGT:
            case Opcodes.IF_ICMPGT:
                return IrNodes.Branch.Condition.GT;
            case Opcodes.IFLE:
            case Opcodes.IF_ICMPLE:
                return IrNodes.Branch.Condition.LE;
            default:
                throw new IllegalArgumentException("Not an integer condition opcode: " + opcode);
        }
    }

    private static UnsupportedIrConstructException unsupported(String message,
                                                               CfgBuilder.Instruction instruction) {
        return new UnsupportedIrConstructException(message, instruction.getOriginalIndex(),
                instruction.getNode().getOpcode());
    }

    private static final class MethodShape {
        private final boolean staticMethod;
        private final IrType returnType;
        private final IrType[] localTypes;
        private final boolean[] wideContinuations;
        private final int parameterLocalCount;

        private MethodShape(boolean staticMethod, IrType returnType, IrType[] localTypes,
                            boolean[] wideContinuations, int parameterLocalCount) {
            this.staticMethod = staticMethod;
            this.returnType = returnType;
            this.localTypes = localTypes;
            this.wideContinuations = wideContinuations;
            this.parameterLocalCount = parameterLocalCount;
        }
    }

    private static final class DefiniteLocals {
        private final Map<CfgBuilder.Block, boolean[]> in;

        private DefiniteLocals(Map<CfgBuilder.Block, boolean[]> in) {
            this.in = in;
        }
    }

    private static final class BlockInputs {
        private final IrValue[] locals;
        private final List<IrValue> stack;
        private final IrPhi[] localPhis;
        private final IrPhi[] stackPhis;

        private BlockInputs(IrValue[] locals, List<IrValue> stack, IrPhi[] localPhis,
                            IrPhi[] stackPhis) {
            this.locals = locals;
            this.stack = stack;
            this.localPhis = localPhis;
            this.stackPhis = stackPhis;
        }
    }

    private static final class ValueState {
        private final IrValue[] locals;
        private final List<IrValue> stack;

        private ValueState(IrValue[] locals, List<IrValue> stack) {
            this.locals = locals;
            this.stack = new ArrayList<>(stack);
        }
    }
}
