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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
                if (definiteLocals.in.get(rawBlock)[local]) {
                    IrType type = shape.localTypes[local];
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
                for (int slot = 0; slot < blockStackTypes.size(); slot++) {
                    IrPhi phi = irMethod.addPhi(irBlock, blockStackTypes.get(slot),
                            IrPhi.SlotKind.STACK, slot);
                    stackPhis[slot] = phi;
                    stack.add(phi.getResult());
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
        } else {
            throw new UnsupportedIrConstructException(
                    "Only void and JVM int-carrier method returns are supported");
        }

        boolean staticMethod = (method.access & Opcodes.ACC_STATIC) != 0;
        int requiredLocals = staticMethod ? 0 : 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (!isIntLike(argument) && !isReference(argument)) {
                throw new UnsupportedIrConstructException(
                        "Only JVM int-carrier and reference arguments are supported");
            }
            requiredLocals += argument.getSize();
        }
        if (method.maxLocals < requiredLocals) {
            throw new UnsupportedIrConstructException("maxLocals is smaller than the parameters");
        }

        IrType[] localTypes = new IrType[method.maxLocals];
        int local = 0;
        if (!staticMethod) {
            localTypes[local++] = IrType.REFERENCE;
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            localTypes[local] = irType(argument);
            local += argument.getSize();
        }
        return new MethodShape(staticMethod, irReturn, localTypes, requiredLocals);
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
                                || opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5
                                || opcode == Opcodes.DUP
                                || isIntBinaryOp(opcode)
                                || isIntUnaryOp(opcode)
                                || opcode == Opcodes.IALOAD
                                || opcode == Opcodes.IASTORE
                                || opcode == Opcodes.ARRAYLENGTH
                                || opcode == Opcodes.ATHROW
                                || opcode == Opcodes.IRETURN
                                || opcode == Opcodes.RETURN;
                        break;
                    case AbstractInsnNode.INT_INSN:
                        supported = opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH;
                        break;
                    case AbstractInsnNode.VAR_INSN:
                        supported = opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE
                                || opcode == Opcodes.ALOAD || opcode == Opcodes.ASTORE;
                        break;
                    case AbstractInsnNode.IINC_INSN:
                        supported = true;
                        break;
                    case AbstractInsnNode.LDC_INSN:
                        supported = ((LdcInsnNode) node).cst instanceof Integer;
                        break;
                    case AbstractInsnNode.JUMP_INSN:
                        supported = opcode == Opcodes.GOTO || isUnaryIntJump(opcode)
                                || isBinaryIntJump(opcode);
                        break;
                    case AbstractInsnNode.FIELD_INSN:
                        FieldInsnNode field = (FieldInsnNode) node;
                        supported = (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
                                && "I".equals(field.desc);
                        break;
                    case AbstractInsnNode.METHOD_INSN:
                        supported = (opcode == Opcodes.INVOKESTATIC
                                || opcode == Opcodes.INVOKEVIRTUAL)
                                && isSimpleInvoke(((MethodInsnNode) node).desc);
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
                local = checkedLocal(local, method, instruction);
                IrType knownType = shape.localTypes[local];
                if (knownType == null) {
                    shape.localTypes[local] = requiredType;
                } else if (knownType != requiredType) {
                    throw unsupported("Local " + local + " is " + knownType
                            + " but this instruction requires " + requiredType, instruction);
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
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5
                || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH
                || opcode == Opcodes.LDC || opcode == Opcodes.ILOAD) {
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.ALOAD) {
            int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
            IrType type = shape.localTypes[local];
            if (type != IrType.REFERENCE) {
                throw unsupported("ALOAD requires a reference local", instruction);
            }
            stack.add(type);
        } else if (opcode == Opcodes.ISTORE) {
            popType(stack, IrType.I32, instruction);
        } else if (opcode == Opcodes.ASTORE) {
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.DUP) {
            if (stack.isEmpty()) {
                throw unsupported("Operand stack underflow", instruction);
            }
            stack.add(stack.get(stack.size() - 1));
        } else if (isIntBinaryOp(opcode)) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.I32);
        } else if (isIntUnaryOp(opcode)) {
            popType(stack, IrType.I32, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.ARRAYLENGTH) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.IALOAD) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.IASTORE) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
        } else if (opcode == Opcodes.ATHROW) {
            popType(stack, IrType.REFERENCE, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("ATHROW requires exactly one operand", instruction);
            }
        } else if (opcode == Opcodes.GETFIELD) {
            popType(stack, IrType.REFERENCE, instruction);
            stack.add(IrType.I32);
        } else if (opcode == Opcodes.PUTFIELD) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.REFERENCE, instruction);
        } else if (node instanceof MethodInsnNode) {
            MethodInsnNode invoke = (MethodInsnNode) node;
            Type[] arguments = Type.getArgumentTypes(invoke.desc);
            for (int i = arguments.length - 1; i >= 0; i--) {
                popType(stack, irType(arguments[i]), instruction);
            }
            if (opcode == Opcodes.INVOKEVIRTUAL) {
                popType(stack, IrType.REFERENCE, instruction);
            }
            stack.add(IrType.I32);
        } else if (isUnaryIntJump(opcode)) {
            popType(stack, IrType.I32, instruction);
        } else if (isBinaryIntJump(opcode)) {
            popType(stack, IrType.I32, instruction);
            popType(stack, IrType.I32, instruction);
        } else if (opcode == Opcodes.IRETURN) {
            popType(stack, IrType.I32, instruction);
            if (!stack.isEmpty()) {
                throw unsupported("IRETURN requires exactly one operand", instruction);
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
                        || node.getOpcode() == Opcodes.ALOAD)) {
                    int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                    if (!current[local]) {
                        throw unsupported("Read of a local not defined on every incoming edge",
                                instruction);
                    }
                } else if (node instanceof VarInsnNode
                        && (node.getOpcode() == Opcodes.ISTORE
                        || node.getOpcode() == Opcodes.ASTORE)) {
                    current[checkedLocal(((VarInsnNode) node).var, method, instruction)] = true;
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
                    || instruction.getNode().getOpcode() == Opcodes.ASTORE)) {
                int local = ((VarInsnNode) instruction.getNode()).var;
                if (local >= 0 && local < result.length) {
                    result[local] = true;
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
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                pushConstant(irMethod, block, state, opcode - Opcodes.ICONST_0,
                        instruction.getOriginalIndex());
            } else if (node instanceof IntInsnNode) {
                pushConstant(irMethod, block, state, ((IntInsnNode) node).operand,
                        instruction.getOriginalIndex());
            } else if (node instanceof LdcInsnNode) {
                pushConstant(irMethod, block, state, (Integer) ((LdcInsnNode) node).cst,
                        instruction.getOriginalIndex());
            } else if (node instanceof VarInsnNode
                    && (opcode == Opcodes.ILOAD || opcode == Opcodes.ALOAD)) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                IrValue value = state.locals[local];
                IrType expected = opcode == Opcodes.ILOAD
                        ? IrType.I32 : IrType.REFERENCE;
                if (value == null || value.getType() != expected) {
                    throw unsupported((opcode == Opcodes.ILOAD ? "ILOAD" : "ALOAD")
                            + " requires a defined " + expected + " local", instruction);
                }
                state.stack.add(value);
            } else if (node instanceof VarInsnNode && opcode == Opcodes.ISTORE) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                if (shape.localTypes[local] != IrType.I32) {
                    throw unsupported("ISTORE cannot write a reference-typed local", instruction);
                }
                state.locals[local] = pop(state, IrType.I32, instruction);
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
            } else if (isIntBinaryOp(opcode)) {
                IrValue right = pop(state, IrType.I32, instruction);
                IrValue left = pop(state, IrType.I32, instruction);
                state.stack.add(blockBinary(irMethod, block, binaryOperation(opcode), left,
                        right, instruction.getOriginalIndex()));
            } else if (isIntUnaryOp(opcode)) {
                IrValue operand = pop(state, IrType.I32, instruction);
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.Unary(result, unaryOperation(opcode), operand,
                        instruction.getOriginalIndex()));
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
            } else if (opcode == Opcodes.IASTORE) {
                IrValue value = pop(state, IrType.I32, instruction);
                IrValue index = pop(state, IrType.I32, instruction);
                IrValue array = pop(state, IrType.REFERENCE, instruction);
                block.addInstruction(new IrNodes.ArrayStore(array, index, value,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
            } else if (node instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) node;
                if (opcode == Opcodes.GETFIELD) {
                    IrValue receiver = pop(state, IrType.REFERENCE, instruction);
                    IrValue result = irMethod.newInstructionValue(IrType.I32);
                    block.addInstruction(new IrNodes.GetField(result, field.owner, field.name,
                            field.desc, receiver, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
                    state.stack.add(result);
                } else {
                    IrValue value = pop(state, IrType.I32, instruction);
                    IrValue receiver = pop(state, IrType.REFERENCE, instruction);
                    block.addInstruction(new IrNodes.PutField(field.owner, field.name,
                            field.desc, receiver, value, instruction.getOriginalIndex(),
                            instruction.getSourceLine()));
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
                IrValue receiver = opcode == Opcodes.INVOKEVIRTUAL
                        ? pop(state, IrType.REFERENCE, instruction) : null;
                IrValue result = irMethod.newInstructionValue(IrType.I32);
                block.addInstruction(new IrNodes.Invoke(result,
                        opcode == Opcodes.INVOKESTATIC
                                ? IrNodes.Invoke.Kind.STATIC : IrNodes.Invoke.Kind.VIRTUAL,
                        invoke.owner, invoke.name, invoke.desc, receiver, arguments,
                        instruction.getOriginalIndex(), instruction.getSourceLine()));
                state.stack.add(result);
            } else if (node instanceof JumpInsnNode) {
                lowerJump(instruction, (JumpInsnNode) node, graph, rawBlock, block, state,
                        irBlocks);
            } else if (opcode == Opcodes.IRETURN) {
                if (shape.returnType != IrType.I32) {
                    throw unsupported("IRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, IrType.I32, instruction),
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

        IrValue right = null;
        IrValue left;
        if (isBinaryIntJump(jump.getOpcode())) {
            right = pop(state, IrType.I32, instruction);
            left = pop(state, IrType.I32, instruction);
        } else {
            left = pop(state, IrType.I32, instruction);
        }
        if (rawBlock.getSuccessors().size() != 2) {
            throw unsupported("Conditional branch does not have two successors", instruction);
        }
        block.setTerminator(new IrNodes.Branch(condition(jump.getOpcode()), left, right,
                irBlocks.get(target), irBlocks.get(rawBlock.getSuccessors().get(1)),
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

    private IrValue blockBinary(IrMethod method, IrBlock block,
                                IrNodes.Binary.Operation operation,
                                IrValue left, IrValue right, int offset) {
        IrValue result = method.newInstructionValue(IrType.I32);
        block.addInstruction(new IrNodes.Binary(result, operation, left, right, offset));
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

    private int checkedLocal(int local, MethodNode method, CfgBuilder.Instruction instruction) {
        if (local < 0 || local >= method.maxLocals) {
            throw unsupported("Local index is outside maxLocals", instruction);
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
        if (isReference(type)) {
            return IrType.REFERENCE;
        }
        throw new IllegalArgumentException("Unsupported JVM carrier type " + type);
    }

    private static boolean isSimpleInvoke(String descriptor) {
        if (Type.getReturnType(descriptor).getSort() != Type.INT) {
            return false;
        }
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            if (argument.getSort() != Type.INT && !isReference(argument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIntBinaryOp(int opcode) {
        return opcode == Opcodes.IADD || opcode == Opcodes.ISUB || opcode == Opcodes.IMUL
                || opcode == Opcodes.IAND || opcode == Opcodes.IOR || opcode == Opcodes.IXOR
                || opcode == Opcodes.ISHL || opcode == Opcodes.ISHR
                || opcode == Opcodes.IUSHR;
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
        private final int parameterLocalCount;

        private MethodShape(boolean staticMethod, IrType returnType, IrType[] localTypes,
                            int parameterLocalCount) {
            this.staticMethod = staticMethod;
            this.returnType = returnType;
            this.localTypes = localTypes;
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
