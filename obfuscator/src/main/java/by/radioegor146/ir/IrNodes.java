package by.radioegor146.ir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Phase-one node set. The nested classes keep the deliberately small initial
 * opcode surface visible in one place.
 */
public final class IrNodes {
    private IrNodes() {
    }

    public static final class Const implements IrInstruction {
        private final IrValue result;
        private final int value;
        private final int bytecodeOffset;

        public Const(IrValue result, int value, int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public int getValue() {
            return value;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Binary implements IrInstruction {
        public enum Operation {
            ADD("iadd"),
            SUBTRACT("isub"),
            MULTIPLY("imul");

            private final String mnemonic;

            Operation(String mnemonic) {
                this.mnemonic = mnemonic;
            }

            public String getMnemonic() {
                return mnemonic;
            }
        }

        private final IrValue result;
        private final Operation operation;
        private final IrValue left;
        private final IrValue right;
        private final int bytecodeOffset;

        public Binary(IrValue result, Operation operation, IrValue left, IrValue right,
                      int bytecodeOffset) {
            this.result = requireI32(result, "result");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.left = requireI32(left, "left");
            this.right = requireI32(right, "right");
            this.bytecodeOffset = bytecodeOffset;
        }

        @Override
        public IrValue getResult() {
            return result;
        }

        public Operation getOperation() {
            return operation;
        }

        public IrValue getLeft() {
            return left;
        }

        public IrValue getRight() {
            return right;
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Goto implements IrTerminator {
        private final IrBlock target;
        private final int bytecodeOffset;

        public Goto(IrBlock target, int bytecodeOffset) {
            this.target = Objects.requireNonNull(target, "target");
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrBlock getTarget() {
            return target;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Collections.singletonList(target);
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Branch implements IrTerminator {
        public enum Condition {
            EQ("eq", "=="),
            NE("ne", "!="),
            LT("lt", "<"),
            GE("ge", ">="),
            GT("gt", ">"),
            LE("le", "<=");

            private final String mnemonic;
            private final String cppOperator;

            Condition(String mnemonic, String cppOperator) {
                this.mnemonic = mnemonic;
                this.cppOperator = cppOperator;
            }

            public String getMnemonic() {
                return mnemonic;
            }

            public String getCppOperator() {
                return cppOperator;
            }
        }

        private final Condition condition;
        private final IrValue left;
        private final IrValue right;
        private final IrBlock trueTarget;
        private final IrBlock falseTarget;
        private final int bytecodeOffset;

        public Branch(Condition condition, IrValue left, IrValue right, IrBlock trueTarget,
                      IrBlock falseTarget, int bytecodeOffset) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.left = requireI32(left, "left");
            this.right = right == null ? null : requireI32(right, "right");
            this.trueTarget = Objects.requireNonNull(trueTarget, "trueTarget");
            this.falseTarget = Objects.requireNonNull(falseTarget, "falseTarget");
            this.bytecodeOffset = bytecodeOffset;
        }

        public Condition getCondition() {
            return condition;
        }

        public IrValue getLeft() {
            return left;
        }

        /**
         * Null means that the JVM single-operand branch compares against zero.
         */
        public IrValue getRight() {
            return right;
        }

        public IrBlock getTrueTarget() {
            return trueTarget;
        }

        public IrBlock getFalseTarget() {
            return falseTarget;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Arrays.asList(trueTarget, falseTarget);
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    public static final class Return implements IrTerminator {
        private final IrValue value;
        private final int bytecodeOffset;

        public Return(IrValue value, int bytecodeOffset) {
            if (value != null) {
                requireI32(value, "value");
            }
            this.value = value;
            this.bytecodeOffset = bytecodeOffset;
        }

        public IrValue getValue() {
            return value;
        }

        @Override
        public List<IrBlock> getSuccessors() {
            return Collections.emptyList();
        }

        @Override
        public int getBytecodeOffset() {
            return bytecodeOffset;
        }
    }

    private static IrValue requireI32(IrValue value, String name) {
        IrValue result = Objects.requireNonNull(value, name);
        if (result.getType() != IrType.I32) {
            throw new IllegalArgumentException(name + " must be i32, got " + result.getType());
        }
        return result;
    }
}
