package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrType;
import by.radioegor146.ir.IrValue;

import java.util.ArrayList;

/**
 * Wraps a block's terminator in an always-true diamond. Both arms reach the
 * original continuation, so a mis-evaluated predicate stays equivalent.
 */
final class FakeBranchInserter {
    private FakeBranchInserter() {
    }

    static void insert(IrMethod method) {
        for (IrBlock block : new ArrayList<>(method.getBlocks())) {
            IrTerminator terminator = block.getTerminator();
            if (terminator == null || shouldSkip(block, terminator)) {
                continue;
            }
            int offset = terminator.getBytecodeOffset();
            IrBlock rest = method.addBlock();
            rest.setTerminator(terminator);
            IrBlock fake = method.addBlock();
            fake.setTerminator(new IrNodes.Goto(rest, offset));
            IrValue predicate = method.newInstructionValue(IrType.I32);
            block.addInstruction(new IrNodes.OpaqueTrue(predicate, offset));
            block.replaceTerminator(new IrNodes.Branch(
                    IrNodes.Branch.Condition.NE, predicate, null,
                    rest, fake, offset));
        }
    }

    private static boolean shouldSkip(IrBlock block, IrTerminator terminator) {
        boolean movesOnly = movesOnly(block);
        if (terminator instanceof IrNodes.Throw) {
            return true;
        }
        if (terminator instanceof IrNodes.Goto) {
            return movesOnly;
        }
        if (terminator instanceof IrNodes.Return) {
            return movesOnly;
        }
        return false;
    }

    private static boolean movesOnly(IrBlock block) {
        for (IrInstruction instruction : block.getInstructions()) {
            if (!(instruction instanceof IrNodes.Assign)
                    && !(instruction instanceof IrNodes.Const)) {
                return false;
            }
        }
        return true;
    }
}
