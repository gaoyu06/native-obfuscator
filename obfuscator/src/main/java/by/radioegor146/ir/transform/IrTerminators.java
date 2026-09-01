package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrTerminator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Successor-slot helpers for CFG rewrites. Unlike
 * {@link IrTerminator#getSuccessors()}, these lists keep duplicate switch
 * targets so each outgoing edge can be split independently.
 */
final class IrTerminators {
    private IrTerminators() {
    }

    static List<IrBlock> successorSlots(IrTerminator terminator) {
        if (terminator instanceof IrNodes.Goto) {
            return Collections.singletonList(((IrNodes.Goto) terminator).getTarget());
        }
        if (terminator instanceof IrNodes.Branch) {
            IrNodes.Branch branch = (IrNodes.Branch) terminator;
            return Arrays.asList(branch.getTrueTarget(), branch.getFalseTarget());
        }
        if (terminator instanceof IrNodes.ReferenceBranch) {
            IrNodes.ReferenceBranch branch = (IrNodes.ReferenceBranch) terminator;
            return Arrays.asList(branch.getTrueTarget(), branch.getFalseTarget());
        }
        if (terminator instanceof IrNodes.ReferenceCompareBranch) {
            IrNodes.ReferenceCompareBranch branch =
                    (IrNodes.ReferenceCompareBranch) terminator;
            return Arrays.asList(branch.getTrueTarget(), branch.getFalseTarget());
        }
        if (terminator instanceof IrNodes.Switch) {
            IrNodes.Switch switchTerminator = (IrNodes.Switch) terminator;
            List<IrBlock> slots = new ArrayList<>(switchTerminator.getTargets());
            slots.add(switchTerminator.getDefaultTarget());
            return slots;
        }
        return Collections.emptyList();
    }

    static IrTerminator replaceSuccessorSlots(IrTerminator terminator,
                                              List<IrBlock> slots) {
        int offset = terminator.getBytecodeOffset();
        if (terminator instanceof IrNodes.Goto) {
            requireSize(slots, 1, terminator);
            return new IrNodes.Goto(slots.get(0), offset);
        }
        if (terminator instanceof IrNodes.Branch) {
            requireSize(slots, 2, terminator);
            IrNodes.Branch branch = (IrNodes.Branch) terminator;
            return new IrNodes.Branch(branch.getCondition(), branch.getLeft(),
                    branch.getRight(), slots.get(0), slots.get(1), offset);
        }
        if (terminator instanceof IrNodes.ReferenceBranch) {
            requireSize(slots, 2, terminator);
            IrNodes.ReferenceBranch branch = (IrNodes.ReferenceBranch) terminator;
            return new IrNodes.ReferenceBranch(branch.getCondition(),
                    branch.getReference(), slots.get(0), slots.get(1), offset);
        }
        if (terminator instanceof IrNodes.ReferenceCompareBranch) {
            requireSize(slots, 2, terminator);
            IrNodes.ReferenceCompareBranch branch =
                    (IrNodes.ReferenceCompareBranch) terminator;
            return new IrNodes.ReferenceCompareBranch(branch.getCondition(),
                    branch.getLeft(), branch.getRight(), slots.get(0),
                    slots.get(1), offset);
        }
        if (terminator instanceof IrNodes.Switch) {
            IrNodes.Switch switchTerminator = (IrNodes.Switch) terminator;
            requireSize(slots, switchTerminator.getTargets().size() + 1, terminator);
            List<IrBlock> targets = new ArrayList<>(slots.subList(0, slots.size() - 1));
            return new IrNodes.Switch(switchTerminator.getSelector(),
                    switchTerminator.getKeys(), targets,
                    slots.get(slots.size() - 1), offset);
        }
        throw new IllegalArgumentException(
                "Terminator has no successor slots: " + terminator.getClass());
    }

    private static void requireSize(List<IrBlock> slots, int expected,
                                    IrTerminator terminator) {
        if (slots.size() != expected) {
            throw new IllegalArgumentException(
                    terminator.getClass().getSimpleName() + " needs " + expected
                            + " successor slots, got " + slots.size());
        }
    }
}
