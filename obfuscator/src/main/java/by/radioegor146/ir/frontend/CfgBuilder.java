package by.radioegor146.ir.frontend;

import by.radioegor146.ir.UnsupportedIrConstructException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Splits ASM instructions into a CFG with normal and ordered exception edges.
 */
public final class CfgBuilder {
    public Graph build(MethodNode method) {
        List<Instruction> instructions = new ArrayList<>();
        Map<LabelNode, Integer> labelPositions = new IdentityHashMap<>();

        int originalIndex = 0;
        int sourceLine = -1;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null;
             node = node.getNext(), originalIndex++) {
            if (node instanceof LabelNode) {
                labelPositions.put((LabelNode) node, instructions.size());
            }
            if (node instanceof LineNumberNode) {
                sourceLine = ((LineNumberNode) node).line;
            }
            if (node.getOpcode() >= 0) {
                instructions.add(new Instruction(instructions.size(), originalIndex, sourceLine, node));
            }
        }

        if (instructions.isEmpty()) {
            if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
                throw new UnsupportedIrConstructException(
                        "An empty method cannot contain exception handlers");
            }
            return new Graph(Collections.singletonList(new Block(0, Collections.<Instruction>emptyList())),
                    Collections.<LabelNode, Block>emptyMap());
        }

        Set<Integer> leaders = new TreeSet<>();
        leaders.add(0);
        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
                addLeader(labelPositions, instructions, leaders, tryCatch.start,
                        "Try-region start");
                addLeader(labelPositions, instructions, leaders, tryCatch.end,
                        "Try-region end");
                addLeader(labelPositions, instructions, leaders, tryCatch.handler,
                        "Exception handler");
            }
        }
        for (Instruction instruction : instructions) {
            AbstractInsnNode node = instruction.getNode();
            if (node instanceof JumpInsnNode) {
                Integer target = labelPositions.get(((JumpInsnNode) node).label);
                if (target == null || target >= instructions.size()) {
                    throw unsupported("Jump target has no executable instruction", instruction);
                }
                leaders.add(target);
                if (instruction.getPosition() + 1 < instructions.size()) {
                    leaders.add(instruction.getPosition() + 1);
                }
            } else if ((isTerminator(node.getOpcode()) || mayThrow(node))
                    && instruction.getPosition() + 1 < instructions.size()) {
                leaders.add(instruction.getPosition() + 1);
            }
        }

        List<Integer> starts = new ArrayList<>(leaders);
        List<Block> blocks = new ArrayList<>();
        Map<Integer, Block> blockByStart = new HashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : instructions.size();
            Block block = new Block(blocks.size(),
                    new ArrayList<>(instructions.subList(start, end)));
            blocks.add(block);
            blockByStart.put(start, block);
        }

        Map<LabelNode, Block> blockByLabel = new IdentityHashMap<>();
        for (Map.Entry<LabelNode, Integer> entry : labelPositions.entrySet()) {
            Block block = blockByStart.get(entry.getValue());
            if (block != null) {
                blockByLabel.put(entry.getKey(), block);
            }
        }

        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            Instruction last = block.getInstructions().get(block.getInstructions().size() - 1);
            AbstractInsnNode node = last.getNode();
            if (node instanceof JumpInsnNode) {
                Block target = blockByLabel.get(((JumpInsnNode) node).label);
                if (target == null) {
                    throw unsupported("Jump target is not a basic-block leader", last);
                }
                block.addSuccessor(target);
                if (node.getOpcode() != Opcodes.GOTO) {
                    if (i + 1 >= blocks.size()) {
                        throw unsupported("Conditional jump has no fallthrough block", last);
                    }
                    block.addSuccessor(blocks.get(i + 1));
                }
            } else if (!isTerminator(node.getOpcode()) && i + 1 < blocks.size()) {
                block.addSuccessor(blocks.get(i + 1));
            }
        }

        if (method.tryCatchBlocks != null) {
            for (Block block : blocks) {
                if (block.getInstructions().isEmpty()) {
                    continue;
                }
                int position = block.getInstructions().get(0).getPosition();
                Set<String> seenTypes = new LinkedHashSet<>();
                for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
                    Integer start = labelPositions.get(tryCatch.start);
                    Integer end = labelPositions.get(tryCatch.end);
                    Block handler = blockByLabel.get(tryCatch.handler);
                    if (start == null || end == null || start >= end || handler == null) {
                        throw new UnsupportedIrConstructException(
                                "Malformed or empty exception-handler region");
                    }
                    if (position >= start && position < end
                            && seenTypes.add(tryCatch.type)) {
                        block.addExceptionEdge(new ExceptionEdge(tryCatch.type, handler));
                    }
                }
            }
        }

        return new Graph(blocks, blockByLabel);
    }

    private static void addLeader(Map<LabelNode, Integer> labelPositions,
                                  List<Instruction> instructions, Set<Integer> leaders,
                                  LabelNode label, String description) {
        Integer position = labelPositions.get(label);
        if (position == null) {
            throw new UnsupportedIrConstructException(description + " label is not in the method");
        }
        if (position < instructions.size()) {
            leaders.add(position);
        }
    }

    private static boolean isReturn(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
    }

    private static boolean isTerminator(int opcode) {
        return isReturn(opcode) || opcode == Opcodes.ATHROW;
    }

    private static boolean mayThrow(AbstractInsnNode node) {
        int opcode = node.getOpcode();
        return opcode == Opcodes.IDIV || opcode == Opcodes.IREM
                || opcode == Opcodes.NEWARRAY
                || opcode == Opcodes.ARRAYLENGTH || opcode == Opcodes.IALOAD
                || opcode == Opcodes.IASTORE || opcode == Opcodes.GETFIELD
                || opcode == Opcodes.PUTFIELD || opcode == Opcodes.GETSTATIC
                || opcode == Opcodes.PUTSTATIC || opcode == Opcodes.INVOKESTATIC
                || opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.ATHROW;
    }

    private static UnsupportedIrConstructException unsupported(String message,
                                                               Instruction instruction) {
        return new UnsupportedIrConstructException(message, instruction.getOriginalIndex(),
                instruction.getNode().getOpcode());
    }

    public static final class Graph {
        private final List<Block> blocks;
        private final Map<LabelNode, Block> blockByLabel;

        private Graph(List<Block> blocks, Map<LabelNode, Block> blockByLabel) {
            this.blocks = Collections.unmodifiableList(blocks);
            this.blockByLabel = blockByLabel;
        }

        public List<Block> getBlocks() {
            return blocks;
        }

        public Block getBlock(LabelNode label) {
            return blockByLabel.get(label);
        }

        public Set<Block> reachableBlocks() {
            Set<Block> reachable = new LinkedHashSet<>();
            visit(blocks.get(0), reachable);
            return reachable;
        }

        private void visit(Block block, Set<Block> reachable) {
            if (!reachable.add(block)) {
                return;
            }
            for (Block successor : block.getSuccessors()) {
                visit(successor, reachable);
            }
            for (ExceptionEdge edge : block.getExceptionEdges()) {
                visit(edge.getHandler(), reachable);
            }
        }
    }

    public static final class Block {
        private final int id;
        private final List<Instruction> instructions;
        private final List<Block> successors = new ArrayList<>();
        private final List<Block> predecessors = new ArrayList<>();
        private final List<ExceptionEdge> exceptionEdges = new ArrayList<>();
        private final List<Block> exceptionPredecessors = new ArrayList<>();

        private Block(int id, List<Instruction> instructions) {
            this.id = id;
            this.instructions = Collections.unmodifiableList(instructions);
        }

        public int getId() {
            return id;
        }

        public List<Instruction> getInstructions() {
            return instructions;
        }

        public List<Block> getSuccessors() {
            return Collections.unmodifiableList(successors);
        }

        public List<Block> getPredecessors() {
            return Collections.unmodifiableList(predecessors);
        }

        public List<ExceptionEdge> getExceptionEdges() {
            return Collections.unmodifiableList(exceptionEdges);
        }

        public List<Block> getExceptionPredecessors() {
            return Collections.unmodifiableList(exceptionPredecessors);
        }

        private void addSuccessor(Block successor) {
            successors.add(successor);
            successor.predecessors.add(this);
        }

        private void addExceptionEdge(ExceptionEdge edge) {
            exceptionEdges.add(edge);
            edge.handler.exceptionPredecessors.add(this);
        }
    }

    public static final class ExceptionEdge {
        private final String catchType;
        private final Block handler;

        private ExceptionEdge(String catchType, Block handler) {
            this.catchType = catchType;
            this.handler = handler;
        }

        /**
         * Null denotes the JVM catch-all entry.
         */
        public String getCatchType() {
            return catchType;
        }

        public Block getHandler() {
            return handler;
        }
    }

    public static final class Instruction {
        private final int position;
        private final int originalIndex;
        private final int sourceLine;
        private final AbstractInsnNode node;

        private Instruction(int position, int originalIndex, int sourceLine, AbstractInsnNode node) {
            this.position = position;
            this.originalIndex = originalIndex;
            this.sourceLine = sourceLine;
            this.node = node;
        }

        public int getPosition() {
            return position;
        }

        public int getOriginalIndex() {
            return originalIndex;
        }

        public int getSourceLine() {
            return sourceLine;
        }

        public AbstractInsnNode getNode() {
            return node;
        }
    }
}
