package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites normal successors through a switch dispatcher. Return and throw
 * stay in place. Exception edges still jump to their handlers directly.
 */
final class ControlFlowFlattener {
    private ControlFlowFlattener() {
    }

    static void flatten(IrMethod method) {
        List<IrBlock> original = new ArrayList<>(method.getBlocks());
        int flattenable = 0;
        for (IrBlock block : original) {
            if (!IrTerminators.successorSlots(block.getTerminator()).isEmpty()) {
                flattenable++;
            }
        }
        if (flattenable < 2) {
            return;
        }

        IrValue state = method.newInstructionValue(IrType.I32);
        IrBlock dispatcher = method.addBlock();
        IrBlock trap = method.addBlock();
        addDefaultReturn(method, trap);

        Map<IrBlock, Integer> ids = new LinkedHashMap<>();
        int nextId = 1;
        for (IrBlock block : original) {
            ids.put(block, nextId++);
        }

        for (IrBlock block : original) {
            IrTerminator terminator = block.getTerminator();
            List<IrBlock> slots = IrTerminators.successorSlots(terminator);
            if (slots.isEmpty()) {
                continue;
            }
            int offset = terminator.getBytecodeOffset();
            if (slots.size() == 1) {
                emitStateGoto(method, block, state, ids.get(slots.get(0)),
                        dispatcher, offset);
                continue;
            }
            List<IrBlock> stubs = new ArrayList<>(slots.size());
            for (IrBlock successor : slots) {
                IrBlock stub = method.addBlock();
                emitStateGoto(method, stub, state, ids.get(successor),
                        dispatcher, offset);
                stubs.add(stub);
            }
            block.replaceTerminator(
                    IrTerminators.replaceSuccessorSlots(terminator, stubs));
        }

        List<Integer> keys = new ArrayList<>();
        List<IrBlock> targets = new ArrayList<>();
        for (Map.Entry<IrBlock, Integer> entry : ids.entrySet()) {
            keys.add(entry.getValue());
            targets.add(entry.getKey());
        }
        dispatcher.setTerminator(new IrNodes.Switch(state, keys, targets, trap, -1));
    }

    private static void emitStateGoto(IrMethod method, IrBlock block, IrValue state,
                                      Integer targetId, IrBlock dispatcher, int offset) {
        if (targetId == null) {
            throw new IllegalStateException("Missing flatten id for successor");
        }
        IrValue constant = method.newInstructionValue(IrType.I32);
        block.addInstruction(new IrNodes.Const(constant, targetId, offset));
        block.addInstruction(new IrNodes.Assign(state, constant, offset));
        if (block.getTerminator() == null) {
            block.setTerminator(new IrNodes.Goto(dispatcher, offset));
        } else {
            block.replaceTerminator(new IrNodes.Goto(dispatcher, offset));
        }
    }

    private static void addDefaultReturn(IrMethod method, IrBlock trap) {
        IrType type = method.getReturnType();
        if (type == IrType.VOID) {
            trap.setTerminator(new IrNodes.Return(null, -1));
            return;
        }
        IrValue value = method.newInstructionValue(type);
        if (type == IrType.I32) {
            trap.addInstruction(new IrNodes.Const(value, 0, -1));
        } else if (type == IrType.I64) {
            trap.addInstruction(new IrNodes.LongConst(value, 0L, -1));
        } else if (type == IrType.F32) {
            trap.addInstruction(new IrNodes.FloatConst(value, 0, -1));
        } else if (type == IrType.F64) {
            trap.addInstruction(new IrNodes.DoubleConst(value, 0L, -1));
        } else {
            trap.addInstruction(new IrNodes.NullReference(value, -1));
        }
        trap.setTerminator(new IrNodes.Return(value, -1));
    }
}
