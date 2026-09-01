package by.radioegor146.ir.transform;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Permutes non-entry blocks. Labels and gotos keep the graph; only emission
 * order changes. The seed is a function of the method identity so tests and
 * rebuilds stay deterministic.
 */
final class BlockOrderShuffler {
    private BlockOrderShuffler() {
    }

    static void shuffle(IrMethod method) {
        List<IrBlock> blocks = method.getBlocks();
        if (blocks.size() < 3) {
            return;
        }
        List<IrBlock> tail = new ArrayList<IrBlock>(
                blocks.subList(1, blocks.size()));
        List<IrBlock> originalTail = new ArrayList<IrBlock>(tail);
        Collections.shuffle(tail, new Random(seed(method)));
        if (tail.equals(originalTail) && tail.size() >= 2) {
            Collections.swap(tail, 0, tail.size() - 1);
        }
        List<IrBlock> reordered = new ArrayList<IrBlock>(blocks.size());
        reordered.add(blocks.get(0));
        reordered.addAll(tail);
        method.replaceBlocks(reordered);
    }

    private static long seed(IrMethod method) {
        return (long) Objects.hash(
                method.getOwner(), method.getName(), method.getDescriptor());
    }
}
