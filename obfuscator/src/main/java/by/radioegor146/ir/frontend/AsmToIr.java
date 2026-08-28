package by.radioegor146.ir.frontend;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
 * Lowers the phase-one bytecode subset to typed block-local SSA. JVM locals and
 * operand-stack entries become block phi values; load/store bytecodes therefore
 * disappear into the SSA environment.
 */
public final class AsmToIr {
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    public IrMethod build(String owner, MethodNode method) {
        MethodShape shape = validateMethodShape(method);
        CfgBuilder.Graph graph = cfgBuilder.build(method);
        validateInstructions(graph);

        Set<CfgBuilder.Block> reachable = graph.reachableBlocks();
        CfgBuilder.Block entry = graph.getBlocks().get(0);
        if (entry.getPredecessors().stream().anyMatch(reachable::contains)) {
            throw new UnsupportedIrConstructException(
                    "A backedge to the JVM method entry is outside the phase-one subset");
        }

        Map<CfgBuilder.Block, Integer> stackHeights = computeStackHeights(graph, reachable);
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

            int stackHeight = stackHeights.get(rawBlock);
            List<IrValue> stack = new ArrayList<>();
            IrPhi[] stackPhis = new IrPhi[stackHeight];
            for (int slot = 0; slot < stackHeight; slot++) {
                IrPhi phi = irMethod.addPhi(irBlock, IrType.I32, IrPhi.SlotKind.STACK, slot);
                stackPhis[slot] = phi;
                stack.add(phi.getResult());
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
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw new UnsupportedIrConstructException(
                    "Exception handlers are outside the phase-one IR subset");
        }

        Type returnType = Type.getReturnType(method.desc);
        IrType irReturn;
        if (returnType.getSort() == Type.VOID) {
            irReturn = IrType.VOID;
        } else if (isIntLike(returnType)) {
            irReturn = IrType.I32;
        } else {
            throw new UnsupportedIrConstructException(
                    "Only void and JVM int-carrier return types are supported");
        }

        boolean staticMethod = (method.access & Opcodes.ACC_STATIC) != 0;
        int requiredLocals = staticMethod ? 0 : 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (!isIntLike(argument)) {
                throw new UnsupportedIrConstructException(
                        "Only JVM int-carrier arguments are supported");
            }
            requiredLocals += argument.getSize();
        }
        if (method.maxLocals < requiredLocals) {
            throw new UnsupportedIrConstructException("maxLocals is smaller than the parameters");
        }

        IrType[] localTypes = new IrType[method.maxLocals];
        Arrays.fill(localTypes, IrType.I32);
        if (!staticMethod) {
            localTypes[0] = IrType.REFERENCE;
        }
        return new MethodShape(staticMethod, irReturn, localTypes, requiredLocals);
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
                                || opcode == Opcodes.IADD
                                || opcode == Opcodes.ISUB
                                || opcode == Opcodes.IMUL
                                || opcode == Opcodes.IRETURN
                                || opcode == Opcodes.RETURN;
                        break;
                    case AbstractInsnNode.INT_INSN:
                        supported = opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH;
                        break;
                    case AbstractInsnNode.VAR_INSN:
                        supported = opcode == Opcodes.ILOAD || opcode == Opcodes.ISTORE;
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
                    default:
                        supported = false;
                }
                if (!supported) {
                    throw unsupported("Unsupported instruction for phase-one IR", instruction);
                }
            }
        }
    }

    private Map<CfgBuilder.Block, Integer> computeStackHeights(CfgBuilder.Graph graph,
                                                               Set<CfgBuilder.Block> reachable) {
        Map<CfgBuilder.Block, Integer> inputHeights = new HashMap<>();
        Queue<CfgBuilder.Block> work = new ArrayDeque<>();
        CfgBuilder.Block entry = graph.getBlocks().get(0);
        inputHeights.put(entry, 0);
        work.add(entry);

        while (!work.isEmpty()) {
            CfgBuilder.Block block = work.remove();
            int height = inputHeights.get(block);
            for (CfgBuilder.Instruction instruction : block.getInstructions()) {
                height += stackDelta(instruction);
                if (height < 0) {
                    throw unsupported("Operand stack underflow", instruction);
                }
            }
            for (CfgBuilder.Block successor : block.getSuccessors()) {
                if (!reachable.contains(successor)) {
                    continue;
                }
                Integer known = inputHeights.get(successor);
                if (known == null) {
                    inputHeights.put(successor, height);
                    work.add(successor);
                } else if (known != height) {
                    CfgBuilder.Instruction last = block.getInstructions().isEmpty() ? null
                            : block.getInstructions().get(block.getInstructions().size() - 1);
                    if (last == null) {
                        throw new UnsupportedIrConstructException(
                                "Mismatched operand-stack heights at CFG merge");
                    }
                    throw unsupported("Mismatched operand-stack heights at CFG merge", last);
                }
            }
        }
        return inputHeights;
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
                if (node instanceof VarInsnNode && node.getOpcode() == Opcodes.ILOAD) {
                    int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                    if (!current[local]) {
                        throw unsupported("Read of a local not defined on every incoming edge",
                                instruction);
                    }
                } else if (node instanceof VarInsnNode && node.getOpcode() == Opcodes.ISTORE) {
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
                    && instruction.getNode().getOpcode() == Opcodes.ISTORE) {
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
            locals[local] = irMethod.addParameter(IrType.I32, "arg" + i, "arg" + i);
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
            } else if (node instanceof VarInsnNode && opcode == Opcodes.ILOAD) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                IrValue value = state.locals[local];
                if (value == null || value.getType() != IrType.I32) {
                    throw unsupported("ILOAD requires a defined i32 local", instruction);
                }
                state.stack.add(value);
            } else if (node instanceof VarInsnNode && opcode == Opcodes.ISTORE) {
                int local = checkedLocal(((VarInsnNode) node).var, method, instruction);
                state.locals[local] = pop(state, instruction);
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
            } else if (opcode == Opcodes.IADD || opcode == Opcodes.ISUB
                    || opcode == Opcodes.IMUL) {
                IrValue right = pop(state, instruction);
                IrValue left = pop(state, instruction);
                IrNodes.Binary.Operation operation = opcode == Opcodes.IADD
                        ? IrNodes.Binary.Operation.ADD
                        : opcode == Opcodes.ISUB ? IrNodes.Binary.Operation.SUBTRACT
                        : IrNodes.Binary.Operation.MULTIPLY;
                state.stack.add(blockBinary(irMethod, block, operation, left, right,
                        instruction.getOriginalIndex()));
            } else if (node instanceof JumpInsnNode) {
                lowerJump(instruction, (JumpInsnNode) node, graph, rawBlock, block, state,
                        irBlocks);
            } else if (opcode == Opcodes.IRETURN) {
                if (shape.returnType != IrType.I32) {
                    throw unsupported("IRETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(pop(state, instruction),
                        instruction.getOriginalIndex()));
            } else if (opcode == Opcodes.RETURN) {
                if (shape.returnType != IrType.VOID) {
                    throw unsupported("RETURN does not match the method descriptor", instruction);
                }
                block.setTerminator(new IrNodes.Return(null, instruction.getOriginalIndex()));
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
            right = pop(state, instruction);
            left = pop(state, instruction);
        } else {
            left = pop(state, instruction);
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
                for (int local = 0; local < destination.localPhis.length; local++) {
                    IrPhi phi = destination.localPhis[local];
                    if (phi != null) {
                        if (output.locals[local] == null) {
                            throw new IllegalStateException("Missing value for definite local "
                                    + local);
                        }
                        phi.addIncoming(irBlocks.get(predecessor), output.locals[local]);
                    }
                }
                if (destination.stackPhis.length != output.stack.size()) {
                    throw new IllegalStateException("Stack shape changed after analysis");
                }
                for (int slot = 0; slot < destination.stackPhis.length; slot++) {
                    destination.stackPhis[slot].addIncoming(irBlocks.get(predecessor),
                            output.stack.get(slot));
                }
            }
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

    private int checkedLocal(int local, MethodNode method, CfgBuilder.Instruction instruction) {
        if (local < 0 || local >= method.maxLocals) {
            throw unsupported("Local index is outside maxLocals", instruction);
        }
        return local;
    }

    private int stackDelta(CfgBuilder.Instruction instruction) {
        int opcode = instruction.getNode().getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5
                || opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH
                || opcode == Opcodes.LDC || opcode == Opcodes.ILOAD) {
            return 1;
        }
        if (opcode == Opcodes.ISTORE || isUnaryIntJump(opcode) || opcode == Opcodes.IRETURN) {
            return -1;
        }
        if (opcode == Opcodes.IADD || opcode == Opcodes.ISUB || opcode == Opcodes.IMUL
        ) {
            return -1;
        }
        if (isBinaryIntJump(opcode)) {
            return -2;
        }
        return 0;
    }

    private static boolean isIntLike(Type type) {
        int sort = type.getSort();
        return sort >= Type.BOOLEAN && sort <= Type.INT;
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
