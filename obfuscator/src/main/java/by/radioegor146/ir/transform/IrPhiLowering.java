package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrExceptionEdge;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits critical edges and writes phi incoming values as {@link IrNodes.Assign}
 * on the source side so later CFG rewrites do not have to retarget phi maps.
 */
final class IrPhiLowering {
    private IrPhiLowering() {
    }

    static void lower(IrMethod method) {
        splitMultiSuccessorEdges(method);
        materializeTerminatorPhis(method);
        materializeExceptionPhis(method);
        clearNonEntryPhis(method);
    }

    private static void splitMultiSuccessorEdges(IrMethod method) {
        List<IrBlock> blocks = new ArrayList<>(method.getBlocks());
        for (IrBlock block : blocks) {
            IrTerminator terminator = block.getTerminator();
            if (terminator == null) {
                continue;
            }
            List<IrBlock> slots = IrTerminators.successorSlots(terminator);
            if (slots.size() <= 1) {
                continue;
            }
            List<IrBlock> stubs = new ArrayList<>(slots.size());
            for (IrBlock successor : slots) {
                IrBlock stub = method.addBlock();
                stub.setTerminator(new IrNodes.Goto(successor,
                        terminator.getBytecodeOffset()));
                retargetIncoming(successor, block, stub);
                stubs.add(stub);
            }
            for (IrBlock successor : slots) {
                for (IrPhi phi : successor.getPhis()) {
                    phi.removeIncoming(block);
                }
            }
            block.replaceTerminator(
                    IrTerminators.replaceSuccessorSlots(terminator, stubs));
        }
    }

    private static void retargetIncoming(IrBlock successor, IrBlock from,
                                         IrBlock to) {
        for (IrPhi phi : successor.getPhis()) {
            IrValue value = phi.getIncoming().get(from);
            if (value != null) {
                phi.addIncoming(to, value);
            }
        }
    }

    private static void materializeTerminatorPhis(IrMethod method) {
        for (IrBlock target : new ArrayList<>(method.getBlocks())) {
            Map<IrBlock, List<IrNodes.Assign>> copies = new LinkedHashMap<>();
            for (IrPhi phi : target.getPhis()) {
                for (Map.Entry<IrBlock, IrValue> incoming
                        : phi.getIncoming().entrySet()) {
                    IrBlock predecessor = incoming.getKey();
                    if (!IrTerminators.successorSlots(predecessor.getTerminator())
                            .contains(target)) {
                        continue;
                    }
                    List<IrNodes.Assign> group = copies.get(predecessor);
                    if (group == null) {
                        group = new ArrayList<>();
                        copies.put(predecessor, group);
                    }
                    group.add(new IrNodes.Assign(phi.getResult(), incoming.getValue(),
                            predecessor.getTerminator().getBytecodeOffset()));
                }
            }
            for (Map.Entry<IrBlock, List<IrNodes.Assign>> entry : copies.entrySet()) {
                emitParallelCopies(method, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void emitParallelCopies(IrMethod method, IrBlock predecessor,
                                           List<IrNodes.Assign> copies) {
        int offset = predecessor.getTerminator().getBytecodeOffset();
        List<IrValue> temps = new ArrayList<>(copies.size());
        for (IrNodes.Assign copy : copies) {
            IrValue temp = method.newInstructionValue(copy.getTarget().getType());
            temps.add(temp);
            predecessor.addInstruction(new IrNodes.Assign(temp, copy.getSource(), offset));
        }
        for (int i = 0; i < copies.size(); i++) {
            predecessor.addInstruction(new IrNodes.Assign(
                    copies.get(i).getTarget(), temps.get(i), offset));
        }
    }

    private static void materializeExceptionPhis(IrMethod method) {
        for (IrBlock block : new ArrayList<>(method.getBlocks())) {
            List<IrExceptionEdge> original = new ArrayList<>(block.getExceptionEdges());
            if (original.isEmpty()) {
                continue;
            }
            boolean rewritten = false;
            List<IrExceptionEdge> updated = new ArrayList<>(original.size());
            for (IrExceptionEdge edge : original) {
                IrBlock handler = edge.getHandler();
                int offset = block.getTerminator() == null
                        ? -1 : block.getTerminator().getBytecodeOffset();
                List<IrNodes.Assign> copies = new ArrayList<>();
                List<IrNodes.CaughtException> caught = new ArrayList<>();
                for (IrPhi phi : handler.getPhis()) {
                    IrValue value = phi.getIncoming().get(block);
                    if (value != null) {
                        copies.add(new IrNodes.Assign(phi.getResult(), value, offset));
                    } else if (phi.getSlotKind() == IrPhi.SlotKind.STACK) {
                        caught.add(new IrNodes.CaughtException(phi.getResult(), offset));
                    }
                }
                if (copies.isEmpty() && caught.isEmpty()) {
                    updated.add(edge);
                    continue;
                }
                IrBlock trampoline = method.addBlock();
                trampoline.setTerminator(new IrNodes.Goto(handler, offset));
                for (IrNodes.CaughtException instruction : caught) {
                    trampoline.addInstruction(instruction);
                }
                emitParallelCopies(method, trampoline, copies);
                updated.add(new IrExceptionEdge(edge.getCatchType(), trampoline));
                rewritten = true;
            }
            if (rewritten) {
                block.clearExceptionEdges();
                for (IrExceptionEdge edge : updated) {
                    block.addExceptionEdge(edge);
                }
            }
        }
    }

    private static void clearNonEntryPhis(IrMethod method) {
        if (method.getBlocks().isEmpty()) {
            return;
        }
        IrBlock entry = method.getBlocks().get(0);
        for (IrBlock block : method.getBlocks()) {
            if (block == entry) {
                continue;
            }
            block.clearPhis();
        }
    }
}
