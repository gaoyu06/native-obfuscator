package by.radioegor146.ir.frontend;

import by.radioegor146.ir.IrBlock;
import by.radioegor146.ir.IrExceptionEdge;
import by.radioegor146.ir.IrInstruction;
import by.radioegor146.ir.IrMethod;
import by.radioegor146.ir.IrNodes;
import by.radioegor146.ir.IrPhi;
import by.radioegor146.ir.IrTerminator;
import by.radioegor146.ir.IrValue;
import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Proves that explicit JVM monitor operations form the same LIFO monitor stack
 * on every normal and exceptional CFG edge. Ambiguous reference phis are
 * rejected instead of relying on a runtime {@code MonitorExit} failure.
 */
final class MonitorStructureValidator {
    private MonitorStructureValidator() {
    }

    static void validate(IrMethod method) {
        if (!hasExplicitMonitors(method)) {
            return;
        }

        AliasOracle aliases = new AliasOracle(method);
        Map<IrBlock, List<HeldMonitor>> inputs = new IdentityHashMap<>();
        Queue<IrBlock> work = new ArrayDeque<>();
        IrBlock entry = method.getBlocks().get(0);
        inputs.put(entry, Collections.<HeldMonitor>emptyList());
        work.add(entry);

        while (!work.isEmpty()) {
            IrBlock block = work.remove();
            List<HeldMonitor> held = new ArrayList<>(inputs.get(block));

            for (IrInstruction instruction : block.getInstructions()) {
                if (instructionMayThrow(instruction)) {
                    validateExceptionalTransfer(block, held, instruction, inputs,
                            work, aliases);
                }
                if (instruction instanceof IrNodes.MonitorEnter) {
                    IrNodes.MonitorEnter enter = (IrNodes.MonitorEnter) instruction;
                    held.add(new HeldMonitor(enter.getMonitor(), enter.getBytecodeOffset()));
                } else if (instruction instanceof IrNodes.MonitorExit) {
                    IrNodes.MonitorExit exit = (IrNodes.MonitorExit) instruction;
                    if (held.isEmpty()) {
                        throw unsupported("MONITOREXIT has no matching MONITORENTER",
                                exit.getBytecodeOffset(), Opcodes.MONITOREXIT);
                    }
                    HeldMonitor entered = held.get(held.size() - 1);
                    if (!aliases.mustAlias(entered.value, exit.getMonitor())) {
                        throw unsupported(
                                "MONITOREXIT does not use the innermost entered monitor",
                                exit.getBytecodeOffset(), Opcodes.MONITOREXIT);
                    }
                    held.remove(held.size() - 1);
                }
            }

            IrTerminator terminator = block.getTerminator();
            if (terminator instanceof IrNodes.Throw) {
                validateExceptionalTransfer(block, held, null, inputs, work, aliases);
            } else if (terminator instanceof IrNodes.Return && !held.isEmpty()) {
                throw unbalancedReturn(held);
            }

            for (IrBlock successor : terminator.getSuccessors()) {
                mergeInput(inputs, work, successor, held, aliases);
            }
        }
    }

    private static void validateExceptionalTransfer(
            IrBlock block, List<HeldMonitor> held, IrInstruction instruction,
            Map<IrBlock, List<HeldMonitor>> inputs, Queue<IrBlock> work,
            AliasOracle aliases) {
        boolean matchedExit = instruction instanceof IrNodes.MonitorExit;
        if (!held.isEmpty() && !matchedExit
                && !hasCatchAll(block.getExceptionEdges())) {
            throw unsupported("An exception can escape while an explicit monitor is held",
                    held.get(held.size() - 1).bytecodeOffset, Opcodes.MONITORENTER);
        }
        for (IrExceptionEdge edge : block.getExceptionEdges()) {
            mergeInput(inputs, work, edge.getHandler(), held, aliases);
        }
    }

    private static boolean hasExplicitMonitors(IrMethod method) {
        for (IrBlock block : method.getBlocks()) {
            for (IrInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IrNodes.MonitorEnter
                        || instruction instanceof IrNodes.MonitorExit) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mergeInput(Map<IrBlock, List<HeldMonitor>> inputs,
                                   Queue<IrBlock> work, IrBlock target,
                                   List<HeldMonitor> incoming, AliasOracle aliases) {
        List<HeldMonitor> known = inputs.get(target);
        if (known == null) {
            inputs.put(target, Collections.unmodifiableList(new ArrayList<>(incoming)));
            work.add(target);
            return;
        }
        if (known.size() != incoming.size()) {
            throw mergeFailure(known, incoming);
        }
        for (int i = 0; i < known.size(); i++) {
            if (!aliases.mustAlias(known.get(i).value, incoming.get(i).value)) {
                throw mergeFailure(known, incoming);
            }
        }
    }

    private static UnsupportedIrConstructException mergeFailure(
            List<HeldMonitor> known, List<HeldMonitor> incoming) {
        List<HeldMonitor> source = incoming.isEmpty() ? known : incoming;
        int offset = source.isEmpty() ? -1
                : source.get(source.size() - 1).bytecodeOffset;
        return unsupported("Control-flow paths have different explicit monitor states",
                offset, Opcodes.MONITORENTER);
    }

    private static UnsupportedIrConstructException unbalancedReturn(
            List<HeldMonitor> held) {
        HeldMonitor monitor = held.get(held.size() - 1);
        return unsupported("Method returns while an explicit monitor is held",
                monitor.bytecodeOffset, Opcodes.MONITORENTER);
    }

    private static boolean hasCatchAll(List<IrExceptionEdge> edges) {
        for (IrExceptionEdge edge : edges) {
            if (edge.getCatchType() == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean instructionMayThrow(IrInstruction instruction) {
        return instruction instanceof IrNodes.ClassConst
                || instruction instanceof IrNodes.IntDivRem
                || instruction instanceof IrNodes.LongDivRem
                || instruction instanceof IrNodes.NewObject
                || instruction instanceof IrNodes.NewArray
                || instruction instanceof IrNodes.NewObjectArray
                || instruction instanceof IrNodes.MultiNewArray
                || instruction instanceof IrNodes.ArrayLength
                || instruction instanceof IrNodes.ArrayLoad
                || instruction instanceof IrNodes.ArrayStore
                || instruction instanceof IrNodes.StringLength
                || instruction instanceof IrNodes.CheckCast
                || instruction instanceof IrNodes.InstanceOf
                || instruction instanceof IrNodes.GetField
                || instruction instanceof IrNodes.PutField
                || instruction instanceof IrNodes.GetStaticField
                || instruction instanceof IrNodes.PutStaticField
                || instruction instanceof IrNodes.Invoke
                || instruction instanceof IrNodes.MonitorEnter
                || instruction instanceof IrNodes.MonitorExit;
    }

    private static UnsupportedIrConstructException unsupported(
            String message, int offset, int opcode) {
        return new UnsupportedIrConstructException(message, offset, opcode);
    }

    private static final class HeldMonitor {
        private final IrValue value;
        private final int bytecodeOffset;

        private HeldMonitor(IrValue value, int bytecodeOffset) {
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }
    }

    /**
     * Computes non-phi origins to a fixed point. A phi is a must-alias only
     * when all of its reachable concrete origins collapse to one value.
     */
    private static final class AliasOracle {
        private final Map<IrValue, Set<IrValue>> origins = new IdentityHashMap<>();

        private AliasOracle(IrMethod method) {
            for (IrValue parameter : method.getParameters()) {
                origins.put(parameter, identitySet(parameter));
            }
            List<IrPhi> phis = new ArrayList<>();
            for (IrBlock block : method.getBlocks()) {
                for (IrPhi phi : block.getPhis()) {
                    phis.add(phi);
                    origins.put(phi.getResult(), identitySet());
                }
                for (IrInstruction instruction : block.getInstructions()) {
                    if (instruction.getResult() != null) {
                        origins.put(instruction.getResult(),
                                identitySet(instruction.getResult()));
                    }
                }
            }

            boolean changed;
            do {
                changed = false;
                for (IrPhi phi : phis) {
                    Set<IrValue> resultOrigins = origins.get(phi.getResult());
                    for (IrValue incoming : phi.getIncoming().values()) {
                        Set<IrValue> incomingOrigins = origins.get(incoming);
                        if (incomingOrigins != null && resultOrigins.addAll(incomingOrigins)) {
                            changed = true;
                        }
                    }
                }
            } while (changed);
        }

        private boolean mustAlias(IrValue left, IrValue right) {
            if (left == right) {
                return true;
            }
            Set<IrValue> leftOrigins = origins.get(left);
            Set<IrValue> rightOrigins = origins.get(right);
            return leftOrigins != null && rightOrigins != null
                    && leftOrigins.size() == 1 && rightOrigins.size() == 1
                    && leftOrigins.iterator().next() == rightOrigins.iterator().next();
        }

        private static Set<IrValue> identitySet(IrValue... values) {
            Set<IrValue> result =
                    Collections.newSetFromMap(new IdentityHashMap<IrValue, Boolean>());
            Collections.addAll(result, values);
            return result;
        }
    }
}
